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

package uk.gov.hmrc.operationalmetrics.persistence

import org.scalatest.Inspectors
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.mongodb.scala.ObservableFuture
import play.api.{Configuration, Environment as PlayEnvironment}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType
import uk.gov.hmrc.operationalmetrics.model.{CommitId, DeploymentConfigFile, DeploymentEvent, Environment, FileName, RepoName, ServiceName, Version}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration


class HistoricDeploymentEventsQueueRepositorySpec
  extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[WorkItem[DeploymentEvent]]
    with ScalaFutures
    with Inspectors
    with IntegrationPatience:

  val anInstant      : Instant                         = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  val config         : Configuration                   = Configuration.load(PlayEnvironment.simple()) // loads application.conf without starting Play
  val repository     : DeploymentEventsQueueRepository = new DeploymentEventsQueueRepository(config, mongoComponent){ override def now(): Instant = anInstant }

  val deploymentEvent: DeploymentEvent =
    DeploymentEvent(
      serviceName  = ServiceName("service-1")
    , environment  = Environment.Production
    , deploymentId = "123"
    , eventType    = ECSEventType.DeploymentComplete
    , version      = Version(1, 0, 0, "1.0.0")
    , time         = anInstant
    , userName     = None
    , slugUri      = "uri"
    , config       = DeploymentConfigFile(
                       repoName = RepoName("service-1")
                     , fileName = FileName("service-1")
                     , commitId = CommitId("123")
                     ) :: Nil
    )
  
  "The deployment event queue repository" should:
    "ensure indexes are created" in:
      repository.collection.listIndexes().toFuture().futureValue.size shouldBe 5

    "pullOutstanding should return eligible work items" in:
      val workItemId = repository.pushNew(deploymentEvent, anInstant.minusMillis(repository.retryInterval.toMillis + 100)).futureValue.id
      val pulledItem = repository.pullOutstanding.futureValue
      pulledItem.map(_.id) should contain(workItemId)

    "pullOutstanding should not return items still in retry window" in:
      repository.pushNew(deploymentEvent, anInstant).futureValue
      val pulledItem = repository.pullOutstanding.futureValue
      pulledItem shouldBe empty

    "be able to save the same requests twice" in:
      repository.pushNew(deploymentEvent, anInstant).futureValue
      repository.pushNew(deploymentEvent, anInstant).futureValue

      val requests = repository.collection.find().toFuture().futureValue
      requests should have(size(2))

      every(requests) should have(
        Symbol("item"      ) (deploymentEvent)
      , Symbol("status"    ) (ProcessingStatus.ToDo)
      , Symbol("receivedAt") (anInstant)
      , Symbol("updatedAt" ) (anInstant)
      )

    "delete documents based on updatedAt time to live index" in:
      val ttl     = config.get[FiniteDuration]("queue.ttl").toSeconds
      val indexes = repository.collection.listIndexes().toFuture().futureValue

      val optTTL  = indexes.find: idx =>
                      idx.get("name").exists(_.asString().getValue == "updatedAt-ttl-idx")

      optTTL shouldBe defined
      optTTL.flatMap(_.get("expireAfterSeconds")).map(_.asInt32().getValue) shouldBe Some(ttl)
