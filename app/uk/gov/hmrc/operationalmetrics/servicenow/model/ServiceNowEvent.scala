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

package uk.gov.hmrc.operationalmetrics.servicenow.model

import play.api.libs.json.{Json, Writes}

import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

case class ServiceNowEvent(
  shortDescription  : String
, description       : String
, cmdbCI            : String
, workStart         : Instant
, workEnd           : Instant
, correlationId     : String
, closeCode         : String = ServiceNowEvent.defaultCloseCode
, correlationDisplay: String = ServiceNowEvent.defaultCorrelationDisplay
)

object ServiceNowEvent:
  val defaultAssignmentGroup: String =
    "DevOps"

  val defaultCloseCode: String =
    "successful"

  val defaultCorrelationDisplay: String =
    "MDTP"

  private val dateFormat =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Europe/London"))

  given writes: Writes[ServiceNowEvent] =
    Writes { sne =>
      Json.arr(
        Json.obj(
          "short_description"   -> sne.shortDescription
        , "description"         -> sne.description
        , "cmdb_ci"             -> sne.cmdbCI
        , "work_start"          -> dateFormat.format(sne.workStart)
        , "work_end"            -> dateFormat.format(sne.workEnd)
        , "close_code"          -> sne.closeCode
        , "correlation_id"      -> sne.correlationId
        , "correlation_display" -> sne.correlationDisplay
        )
      )
    }
