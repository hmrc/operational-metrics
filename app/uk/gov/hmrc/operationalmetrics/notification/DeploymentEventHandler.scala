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
import uk.gov.hmrc.operationalmetrics.model.DeploymentEvent
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType
import uk.gov.hmrc.operationalmetrics.persistence.DeploymentEventsQueueRepository

import javax.inject.{Inject, Singleton}
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

  private def prefixLog(
    message: Message
  , payload: DeploymentEvent
  ): String =
    s"Deployment Event message with ID: ${message.messageId()} event type: ${payload.eventType.value} " +
    s"and details: ${payload.serviceName.asString} ${payload.version.original} ${payload.environment.asString}."

  override private[notification] def processMessage(message: Message): Future[MessageAction] =
    logger.info(s"Starting processing Deployment Event message with ID: ${message.messageId()}")
    (for
      payload <- EitherT.fromEither[Future]:
                   Json
                     .parse(message.body)
                     .validate(RawDeploymentEventParser.deploymentEventReads)
                     .asEither
                     .map(_.copy(messageId = message.messageId()))
                     .left
                     .map: error =>
                       s"Could not parse Deployment Event message with ID ${message.messageId()} and body: ${message.body}. Reason: $error"
      _       <- payload.eventType match
                   case ECSEventType.DeploymentComplete
                      | ECSEventType.UnDeploymentFailed
                      | ECSEventType.UnDeploymentComplete => EitherT.right[String]:
                                                               repository
                                                                 .pushNew(payload) // insert into work item repo
                                                                 .map: _ =>
                                                                   logger.info(s"Successfully pushed to work item repo: ${prefixLog(message, payload)}")
                   case _                                 => EitherT.right[String](Future.unit)
      _       =  logger.info(s"Successfully processed: ${prefixLog(message, payload)}")
    yield
      MessageAction.Delete(message)
    ).value.map:
      case Left(error)   => logger.error(error); MessageAction.Ignore(message)
      case Right(action) => action
