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

import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Sorts}
import play.api.libs.json.Format
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.operationalmetrics.model.{ServiceLeadTimes, ServiceName}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceLeadTimesRepository @Inject()(
  mongoComponent: MongoComponent
)(using ExecutionContext
) extends PlayMongoRepository[ServiceLeadTimes](
  collectionName = "service-lead-times"
, mongoComponent = mongoComponent
, domainFormat   = ServiceLeadTimes.mongoFormat
, indexes        = Seq(
                     IndexModel(ascending("serviceName"), IndexOptions().unique(true))
                   )
, extraCodecs    = Seq(
                     Codecs.playFormatCodec(summon[Format[ServiceName]])
                   )
) with Logging:

  // profiles are deleted when no longer relevant
  override lazy val requiresTtlIndex = false
  
  def findAll(): Future[Seq[ServiceLeadTimes]] =
    collection
      .find()
      .sort(Sorts.ascending("serviceName"))
      .toFuture()

  def putAll(leadTimes: Seq[ServiceLeadTimes])(using ExecutionContext): Future[Unit] =
    MongoUtils.replace[ServiceLeadTimes](
      collection  = collection
    , newVals     = leadTimes
    , compareById = (a, b) => a.serviceName == b.serviceName
    , filterById  = entry  => Filters.equal("serviceName", entry.serviceName)
    ).map(_ => ())
