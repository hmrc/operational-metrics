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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.{Done, NotUsed}
import play.api.{Configuration, Logging}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{DeleteMessageRequest, Message, ReceiveMessageRequest}

import java.net.{URI, URL}
import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.util.control.NonFatal

enum MessageAction:
  case Delete(message: Message)
  case Ignore(message: Message)

case class SqsConfig(
  keyPrefix    : String
, configuration: Configuration
):
  lazy val endpointOverride: Option[String] =
    configuration.getOptional[String](s"$keyPrefix.endpointOverride").filter(_.trim.nonEmpty)

  lazy val queueUrl           : URL            = URI.create(configuration.get[String](s"$keyPrefix.queueUrl")).toURL
  lazy val maxNumberOfMessages: Int            = configuration.get[Int](s"$keyPrefix.maxNumberOfMessages")
  lazy val waitTimeSeconds    : Int            = configuration.get[Int](s"$keyPrefix.waitTimeSeconds")

abstract class SqsConsumer(
  name       : String
, config     : SqsConfig
)(using
  actorSystem: ActorSystem,
  ec         : ExecutionContext
) extends Logging:
  
  private val awsSqsClient: SqsAsyncClient =
    val client =
      config.endpointOverride
        .fold(SqsAsyncClient.builder()): localstack => // see SeedLocalStack script for testing locally
          SqsAsyncClient.builder()
            .endpointOverride(URI.create(localstack))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .region(Region.of("eu-west-2"))
        .build()
    actorSystem.registerOnTermination(client.close())
    client

  private def getMessages(req: ReceiveMessageRequest): Future[Seq[Message]] =
    logger.info("receiving messages")
    awsSqsClient.receiveMessage(req).asScala
      .map(_.messages.asScala.toSeq)
      .map: res =>
        logger.info(s"received ${res.size} messages")
        res

  private def deleteMessage(message: Message): Future[Unit] =
    awsSqsClient.deleteMessage(
        DeleteMessageRequest.builder()
          .queueUrl(config.queueUrl.toString)
          .receiptHandle(message.receiptHandle)
          .build()
      ).asScala
      .map(_ => ())

  private def dedupe(source: Source[Message, NotUsed]): Source[Message, NotUsed] =
    Source
      .single(Message.builder.messageId("----------").build) // dummy value since the dedupe will ignore the first entry
      .concat(source)
      .sliding(2, 1)
      .mapConcat:
        case prev +: current +: _
          if prev.messageId == current.messageId =>
          logger.warn(s"Read the same $name message ID twice ${prev.messageId} - ignoring duplicate")
          List.empty
        case prev +: current +: _ =>
          List(current)

  def runQueue(): Future[Done] =
    dedupe(
      Source.repeat(
        ReceiveMessageRequest.builder()
          .queueUrl(config.queueUrl.toString)
          .maxNumberOfMessages(config.maxNumberOfMessages)
          .waitTimeSeconds(config.waitTimeSeconds)
          .build()
      ).mapAsync(parallelism = 1)(getMessages)
       .mapConcat(xs => xs)
    )
      .mapAsync(parallelism = 1): message =>
        processMessage(message).flatMap:
          case MessageAction.Delete(message) => deleteMessage(message)
          case MessageAction.Ignore(_)       => Future.unit
        .recover:
          case NonFatal(e) => logger.error(s"Failed to process $name messages", e)
      .run()
      .andThen: res =>
        logger.info(s"Queue $name terminated: $res - restarting")
        actorSystem.scheduler.scheduleOnce(10.seconds)(runQueue())

  runQueue()

  private[notification] def processMessage(message: Message): Future[MessageAction]
end SqsConsumer
