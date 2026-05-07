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


import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.{WorkItem, WorkItemFields, WorkItemRepository}
import org.mongodb.scala.model.*
import uk.gov.hmrc.operationalmetrics.model.DeploymentEvent

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

@Singleton
class DeploymentEventsQueueRepository @Inject()(
  configuration : Configuration
, mongoComponent: MongoComponent
 )(using
  ExecutionContext
) extends WorkItemRepository[DeploymentEvent](
  collectionName = "deploymentEventsQueue"
, mongoComponent = mongoComponent
, itemFormat     = DeploymentEvent.mongoFormat
, workItemFields = WorkItemFields.default
, extraIndexes   = Seq(
                     IndexModel(
                       Indexes.ascending("updatedAt")
                     , IndexOptions()
                         .name("updatedAt-ttl-idx")
                         .expireAfter(configuration.get[FiniteDuration]("queue.ttl").toSeconds, TimeUnit.SECONDS)
                     )
                   )
):
  override def now(): Instant =
    Instant.now()

  lazy val retryInterval = configuration.get[FiniteDuration]("queue.retryInterval")

  override val inProgressRetryAfter: Duration =
    Duration.ofMillis(retryInterval.toMillis)

  def pullOutstanding: Future[Option[WorkItem[DeploymentEvent]]] =
    super.pullOutstanding(
      failedBefore    = now().minusMillis(retryInterval.toMillis)
    , availableBefore = now()
    )
