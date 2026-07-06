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

package uk.gov.hmrc.operationalmetrics.servicenow

import play.api.Configuration
import play.api.http.Status
import play.api.libs.json.{Json, Writes}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.operationalmetrics.servicenow.model.ServiceNowEvent

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceNowConnector @Inject() (
  httpClientV2: HttpClientV2
, config      : Configuration
)(using
  ec: ExecutionContext
):
  import HttpReads.Implicits.*

  private val url = config.get[String]("servicenow.url")

  given HeaderCarrier = HeaderCarrier()
  
  def sendToServiceNow(body: ServiceNowEvent): Future[Unit] =
    given Writes[ServiceNowEvent] = ServiceNowEvent.writes
    httpClientV2
      .post(url"$url/api")
      .setHeader("Authorization"  -> "Something"       )
      .setHeader("Content-Type"   -> "application/json")
      .withBody(Json.toJson(body))
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
        .flatMap:
          case Right(r) if r.status == Status.CREATED => Future.unit
          case Right(r)                               => Future.failed(RuntimeException(s"Unexpected response from ServiceNow: ${r.status}"))
          case Left(e)                                => Future.failed(RuntimeException(s"ServiceNow upstream error: ${e.statusCode} ${e.message}"))
