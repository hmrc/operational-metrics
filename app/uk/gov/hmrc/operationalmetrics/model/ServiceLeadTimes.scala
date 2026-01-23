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

package uk.gov.hmrc.operationalmetrics.model

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, Writes, __}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class LeadTimeMeasurement(
  environment    : Environment
, version        : Version
, slugCreatedAt  : Instant
, firstDeployedAt: Instant
, days           : Int
)

object LeadTimeMeasurement:
  val apiWrites: Writes[LeadTimeMeasurement] =
    ( (__ \ "environment"    ).write[Environment]
    ~ (__ \ "version"        ).write[Version]
    ~ (__ \ "slugCreatedAt"  ).write[Instant]
    ~ (__ \ "firstDeployedAt").write[Instant]
    ~ (__ \ "days"           ).write[Int]
    )(ltm => Tuple.fromProductTyped(ltm))
  
  val mongoFormat: Format[LeadTimeMeasurement] =
    given Format[Instant] = MongoJavatimeFormats.instantFormat
    ( (__ \ "environment"    ).format[Environment]
    ~ (__ \ "version"        ).format[Version]
    ~ (__ \ "slugCreatedAt"  ).format[Instant]
    ~ (__ \ "firstDeployedAt").format[Instant]
    ~ (__ \ "days"           ).format[Int]
    )(LeadTimeMeasurement.apply, ltm => Tuple.fromProductTyped(ltm))

case class ServiceLeadTimes(
  serviceName  : ServiceName
, leadTimes: Seq[LeadTimeMeasurement]
)

object ServiceLeadTimes:
  val apiWrites: Writes[ServiceLeadTimes] =
    given Writes[LeadTimeMeasurement] = LeadTimeMeasurement.apiWrites
    ( (__ \ "serviceName").write[ServiceName]
    ~ (__ \ "leadTimes"  ).write[Seq[LeadTimeMeasurement]]
    )(slt => Tuple.fromProductTyped(slt))
  
  val mongoFormat: Format[ServiceLeadTimes] =
    given Format[LeadTimeMeasurement] = LeadTimeMeasurement.mongoFormat
    ( (__ \ "serviceName").format[ServiceName]
    ~ (__ \ "leadTimes"  ).format[Seq[LeadTimeMeasurement]]
    )(ServiceLeadTimes.apply, slt => Tuple.fromProductTyped(slt))    
