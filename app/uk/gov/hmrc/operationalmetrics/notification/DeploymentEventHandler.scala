/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.operationalmetrics.notification

import cats.data.EitherT
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import play.api.libs.json.Json
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.hmrc.operationalmetrics.model.{DeploymentEvent, UserName}
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType
import uk.gov.hmrc.operationalmetrics.persistence.DeploymentEventsQueueRepository

import javax.inject.{Inject, Singleton}
import java.util.Locale
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DeploymentEventHandler @Inject()(
  configuration  : Configuration
, repository     : DeploymentEventsQueueRepository 
)(using
  actorSystem    : ActorSystem
, ec             : ExecutionContext
) extends SqsConsumer(
  name   = "Deployment"
, config = SqsConfig("aws.sqs.deployment", configuration)
)(using actorSystem, ec):

  private lazy val eventFilter: DeploymentEventHandler.EventFilter =
    DeploymentEventHandler.EventFilter.fromConfig(configuration)

  private def prefixLog(
    message: Message
  , payload: DeploymentEvent
  ): String =
    s"Deployment Event message with ID: ${message.messageId()} event type: ${payload.eventType.value} " +
    s"and details: ${payload.serviceName.asString} ${payload.version.original} ${payload.environment.asString}."

  private def enqueue(payload: DeploymentEvent, message: Message): Future[Unit] =
    if eventFilter.allows(payload) then
      repository
        .pushNew(payload)
        .map: _ =>
          logger.info(s"Successfully pushed to work item repo: ${prefixLog(message, payload)}")
    else
      Future.successful(logger.info(s"Skipping filtered deployment event: ${prefixLog(message, payload)}"))

  override private[notification] def processMessage(message: Message): Future[MessageAction] =
    logger.info(s"Starting processing Deployment Event message with ID: ${message.messageId()}")
    (for
      payload <- EitherT.fromEither[Future]:
                   Json
                     .parse(message.body)
                     .validate(RawDeploymentEventParser.deploymentEventReads)
                     .asEither
                     .map(_.copy(messageId = message.messageId()))
                     .map: event =>
                             if   event.userName == UserName.unknown
                             then logger.warn(s"Unknown deployer_principal for message ID: ${message.messageId()}")
                                  event
                     .left
                     .map: error =>
                       s"Could not parse Deployment Event message with ID ${message.messageId()} and body: ${message.body}. Reason: $error"
      _       <- payload.eventType match
                   case ECSEventType.DeploymentComplete
                      | ECSEventType.UnDeploymentFailed
                      | ECSEventType.UnDeploymentComplete => EitherT.right[String](enqueue(payload, message))
                   case _                                 => EitherT.right[String](Future.unit)
      _       =  logger.info(s"Successfully processed: ${prefixLog(message, payload)}")
    yield
      MessageAction.Delete(message)
    ).value.map:
      case Left(error)   => logger.error(error); MessageAction.Ignore(message)
      case Right(action) => action

object DeploymentEventHandler:
  final case class EventFilter(
    allowedEnvironments: Seq[String]
  , allowedServices     : Seq[String]
  , deniedServices     : Seq[String]
  ):
    private def allows(allowList: Seq[String], value: String): Boolean =
      allowList.isEmpty || allowList.contains(EventFilter.normalise(value))

    private def denies(denyList: Seq[String], value: String): Boolean =
      denyList.contains(EventFilter.normalise(value))

    def allows(event: DeploymentEvent): Boolean =
      allows(allowedEnvironments, event.environment.asString) &&
        allows(allowedServices, event.serviceName.asString) &&
        !denies(deniedServices, event.serviceName.asString)

  object EventFilter:
    private val allowListConfigKey =
      "deployment-event-handler.allow-list"

    private val denyListConfigKey =
      "deployment-event-handler.deny-list"

    private def normalise(value: String): String =
      value.trim.toLowerCase(Locale.ROOT)

    def fromConfig(configuration: Configuration): EventFilter =
      EventFilter(
        allowedEnvironments = configuration.getOptional[Seq[String]](s"$allowListConfigKey.environments").getOrElse(Seq("production")).map(normalise)
      , allowedServices     = configuration.getOptional[Seq[String]](s"$allowListConfigKey.services").getOrElse(Seq.empty).map(normalise)
      , deniedServices      = configuration.getOptional[Seq[String]](s"$denyListConfigKey.services").getOrElse(Seq.empty).map(normalise)
      )
