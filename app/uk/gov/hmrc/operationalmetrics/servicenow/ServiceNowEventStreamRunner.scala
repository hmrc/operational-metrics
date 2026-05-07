/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.operationalmetrics.servicenow

import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.operationalmetrics.model.{DeploymentEvent, UserName, Version}
import uk.gov.hmrc.operationalmetrics.persistence.DeploymentEventsQueueRepository
import uk.gov.hmrc.operationalmetrics.connector.{ArtefactProcessorConnector, ReleasesConnector}
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType
import uk.gov.hmrc.operationalmetrics.servicenow.model.ServiceNowEvent

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}
import scala.util.Failure
import cats.implicits.*

@Singleton
class ServiceNowEventStreamRunner @Inject()(
  repo                      : DeploymentEventsQueueRepository
, config                    : Configuration
, releasesConnector         : ReleasesConnector
, artefactProcessorConnector: ArtefactProcessorConnector
, serviceNowConnector       : ServiceNowConnector
)(implicit
  ec : ExecutionContext
, mat: Materializer
) extends Logging:

  private val initialDelay: FiniteDuration = config.get[Duration]("servicenow-stream.source-tick.initialDelay").toMillis.millis
  private val interval    : FiniteDuration = config.get[Duration]("servicenow-stream.source-tick.interval"    ).toMillis.millis

  private given             HeaderCarrier  = HeaderCarrier()

  if   config.get[Boolean]("servicenow-stream.enabled") then
       run(Source.tick(initialDelay = initialDelay, interval = interval, tick = ()))
       logger.info("Started ServiceNow stream")
  else logger.warn("ServiceNow stream is disabled")

  def run(tickSource: Source[Unit, _]): Future[Done] =
    tickSource
      .flatMapConcat: _ => // waits for the inner source to complete (hit None), then moves to the next tick from tickSource
        Source.unfoldAsync(()): _ => // pulls repeatedly until None
          repo.pullOutstanding
            .map:
              case Some(wi) => Some(((), wi))
              case None     => None
            .recoverWith:
              case ex =>
                logger.error(s"Unable to pull outstanding work item from mongo Reason: ${ex.getMessage}")
                Future.successful(None) // terminates this tick's unfold, stream retries via .andThen
      .mapAsync(1): wi =>
        process(wi.item)
          .flatMap:_ =>
            repo.completeAndDelete(wi.id)
          .recoverWith:
            case e if wi.failureCount < 3 => logger.error(s"${processingStatusFailedLog(wi)} Reason: ${e.getMessage}")
                                             repo.markAs(wi.id, ProcessingStatus.Failed)
            case e                        => logger.error(s"${processingStatusPermanentlyFailedLog(wi)} Reason: ${e.getMessage}")
                                             repo.markAs(wi.id, ProcessingStatus.PermanentlyFailed)
      .runWith(Sink.ignore)
      .andThen:
        case Failure(ex) => logger.warn(s"ServiceNow Event stream failed: ${ex.getMessage} - restarting")
          run(tickSource)

  private[servicenow] def deploymentDescription(
    event   : DeploymentEvent
  , previous: Option[Version]
  ): String =
    val service = event.serviceName.asString
    val ver     = event.version.original
    val env     = event.environment
    event.eventType match // assuming other ECS event types are filtered at SNS level
      case ECSEventType.UnDeploymentComplete => s"$service $ver undeployed in $env"
      case ECSEventType.UnDeploymentFailed   => s"$service $ver failed to undeploy in $env"
      case ECSEventType.DeploymentComplete   => previous match
                                                  case Some(prev) if prev == event.version => s"$service $ver re-deployed in $env"
                                                  case Some(prev)                          => s"$service $ver deployed in $env (previously deployed version ${prev.original})"
                                                  case None                                => s"$service $ver deployed in $env"
      case other                             => throw RuntimeException(s"Unexpected event type for service now description: $other")

  private[servicenow] def process(event: DeploymentEvent): Future[Unit] =
    for
      previous        <- releasesConnector.previousDeployment(event.serviceName, event.environment, event.time)
      metaArtefact    <- artefactProcessorConnector
                           .getMetaArtefact(event.serviceName.asString, event.version)
                           .flatTap:
                             case None    => Future.successful(logger.warn(s"Missing meta artefact for ${event.serviceName.asString} ${event.version.original}, deriving branch from version"))
                             case Some(_) => Future.unit
      branch          =  metaArtefact
                           .flatMap(_.gitBranch)
                           .getOrElse(if event.version.isHotfix then "hotfix" else "main")
      commitIds       =  metaArtefact.flatMap(_.gitCommit).toSeq ++ event.config.map(_.commitId)
      serviceNowEvent =  ServiceNowEvent(
                           requestedBy          = event.userName.getOrElse(UserName("default"))
                         , shortDescription     = deploymentDescription(event, previous.map(_.version))
                         , pipelineExecutionId  = event.deploymentId
                         , repository           = s"https://github.com/hmrc/${event.serviceName.asString}"
                         , branch               = branch
                         , commitIds            = commitIds
                         , artefact             = event.slugUri
                         , testResults          = "Pass"
                         , startDateTime        = None
                         , endDateTime          = event.time
                         , deploymentStatus     = event.eventType
                         , implementationResult = event.eventType
                         , service              = event.serviceName
                         , configurationItem    = event.serviceName
                         )
      _               <- serviceNowConnector.sendToServiceNow(serviceNowEvent)
    yield ()
  
  private def processingStatusFailedLog(wi: WorkItem[DeploymentEvent]): String =
    s"Failed to send ServiceNow event, will retry in ${config.getMillis("queue.retryInterval") / 1000}s - " +
    s"Deployment Event: ${wi.item.eventType.value} for ${wi.item.serviceName.asString} ${wi.item.version.original} in ${wi.item.environment.asString}, attempt: ${wi.failureCount}"
  
  private def processingStatusPermanentlyFailedLog(wi: WorkItem[DeploymentEvent]): String =
    s"Failed to send ServiceNow event after ${wi.failureCount} attempts, marking as permanently failed - " +
    s"Deployment Event: ${wi.item.eventType.value} for ${wi.item.serviceName.asString} ${wi.item.version.original} in ${wi.item.environment.asString}"
end ServiceNowEventStreamRunner
