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
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import play.api.Logging
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, __}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.operationalmetrics.persistence.ServiceNowMappingsRepository.ServiceNowMapping

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceNowMappingsRepository @Inject()(
  mongoComponent: MongoComponent
)(using ExecutionContext
) extends PlayMongoRepository[ServiceNowMapping](
    collectionName = "service-now-mappings"
  , mongoComponent = mongoComponent
  , domainFormat   = ServiceNowMapping.mongoFormat
  , indexes        = Seq(
    IndexModel(ascending("name"), IndexOptions().unique(true))
  )
) with Logging:

  // mappings are deleted when they no longer exist
  override lazy val requiresTtlIndex = false

  def find(name: String): Future[Option[ServiceNowMapping]] =
    collection
      .find(Filters.equal("name", name))
      .headOption()
  
  def putAll(mappings: Seq[ServiceNowMapping])(using ExecutionContext): Future[Unit] =
    MongoUtils.replace[ServiceNowMapping](
      collection  = collection
    , newVals     = mappings
    , compareById = (a, b) => a.name == b.name
    , filterById  = entry  => Filters.equal("name", entry.name)
    ).map(_ => ())

object ServiceNowMappingsRepository:
  val defaultCmdbCI: String =
    "Default"

  case class ServiceNowMapping(
    name  : String
  , cmdbCI: String
  )
  
  object ServiceNowMapping:
    val mongoFormat: Format[ServiceNowMapping] =
      ( (__ \ "name"  ).format[String]
      ~ (__ \ "cmdbCI").format[String]
      )(ServiceNowMapping.apply, snm => Tuple.fromProductTyped(snm))    
