package uk.gov.hmrc.operationalmetrics.scripts

import play.api.libs.json.*
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, SendMessageRequest}
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region

import java.net.URI
import scala.jdk.FutureConverters.*
import scala.concurrent.Await
import scala.concurrent.duration.*

object SeedLocalStack extends App:
  private val localstack = "https://localhost.localstack.cloud:4566"
  private val resource   = "uk/gov/hmrc/operationalmetrics/resources/mdtp-deployment-events.json"
  private val queueName  = "mdtp-deployment-events"
  private val queueUrl   = s"https://sqs.eu-west-2.localhost.localstack.cloud:4566/000000000000/$queueName"

  private val client = SqsAsyncClient.builder()
    .endpointOverride(URI.create(localstack))
    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
    .region(Region.of("eu-west-2"))
    .build()
  
  Await.result(
    client.createQueue(CreateQueueRequest.builder().queueName(queueName).build()).asScala,
    5.seconds
  )

  println(s"Queue created: $queueUrl")

  private val json     = scala.io.Source.fromResource(resource).mkString
  private val messages = Json.parse(json).as[JsArray].value

  println(s"\nSeeding from: $resource")

  messages.foreach: message =>
    val request = 
      SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(Json.stringify(message))
        .build()
    Await.result(client.sendMessage(request).asScala, 5.seconds)
    println(s"\nSent: ${Json.stringify(message)}")

  println(s"\nFinished seeding ${messages.size} messages")

  client.close()
