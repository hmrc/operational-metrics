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
import play.api.libs.json.{JsError, Json, Reads, Writes}
import play.api.libs.ws.DefaultBodyWritables.writeableOf_urlEncodedForm
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.operationalmetrics.servicenow.model.ServiceNowEvent

import javax.inject.{Inject, Singleton}
import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

@Singleton
class ServiceNowConnector @Inject() (
  httpClientV2: HttpClientV2
, config      : Configuration
, clock       : Clock
)(using
  ec: ExecutionContext
):
  import HttpReads.Implicits.*

  private val baseUrl           = config.get[String]("servicenow.url").stripSuffix("/")
  private val clientId          = config.get[String]("servicenow.oauth.client-id")
  private val clientSecret      = config.get[String]("servicenow.oauth.client-secret")
  private val tokenExpiryBuffer = config.get[FiniteDuration]("servicenow.oauth.expiry-buffer")
  private var cachedToken       = Option.empty[CachedAccessToken]
  private var refreshingToken   = Option.empty[Future[CachedAccessToken]]

  given HeaderCarrier = HeaderCarrier()

  def sendToServiceNow(body: ServiceNowEvent): Future[Unit] =
    given Writes[ServiceNowEvent] = ServiceNowEvent.writes
    accessToken().flatMap: token =>
      httpClientV2
        .post(url"$baseUrl/api/ukrc/hmrc_change_registration/change_registration")
        .setHeader("Authorization"  -> token.authorizationHeader)
        .setHeader("Content-Type"   -> "application/json")
        .withBody(Json.toJson(body))
        .withProxy
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
          .flatMap:
            case Right(r) if r.status == Status.CREATED => Future.unit
            case Right(r)                               => Future.failed(RuntimeException(s"Unexpected response from ServiceNow: ${r.status}"))
            case Left(e)                                => Future.failed(RuntimeException(s"ServiceNow upstream error: ${e.statusCode} ${e.message}"))

  private def accessToken(): Future[CachedAccessToken] =
    this.synchronized:
      cachedToken.filter(_.isUsable(clock.instant(), tokenExpiryBuffer)) match
        case Some(token) => Future.successful(token)
        case None        =>
          refreshingToken.getOrElse:
            val refresh = fetchAccessToken().andThen:
              case _ =>
                this.synchronized:
                  refreshingToken = None
            refreshingToken = Some(refresh)
            refresh

  private def fetchAccessToken(): Future[CachedAccessToken] =
    httpClientV2
      .post(url"$baseUrl/oauth_token.do")
      .setHeader("Content-Type" -> "application/x-www-form-urlencoded")
      .withBody(Map(
        "grant_type"    -> Seq("client_credentials")
      , "client_id"     -> Seq(clientId)
      , "client_secret" -> Seq(clientSecret)
      ))
      .withProxy
      .execute[Either[UpstreamErrorResponse, HttpResponse]]
      .flatMap:
        case Right(r) if r.status == Status.OK => readAccessToken(r)
        case Right(r)                          => Future.failed(RuntimeException(s"Unexpected response from ServiceNow token endpoint: ${r.status}"))
        case Left(e)                           => Future.failed(RuntimeException(s"ServiceNow token upstream error: ${e.statusCode}"))

  private def readAccessToken(response: HttpResponse): Future[CachedAccessToken] =
    response.json.validate[ServiceNowAccessToken].fold(
      errors => Future.failed(RuntimeException(s"Invalid response from ServiceNow token endpoint: ${JsError.toJson(errors)}")),
      token  =>
        val cached = token.toCachedToken(clock.instant())
        this.synchronized:
          cachedToken = Some(cached)
        Future.successful(cached)
    )

private final case class CachedAccessToken(
  authorizationHeader: String
, expiresAt          : Instant
):
  def isUsable(
    now   : Instant
  , buffer: FiniteDuration
  ): Boolean =
    now.plusSeconds(buffer.toSeconds).isBefore(expiresAt)

private final case class ServiceNowAccessToken(
  access_token: String
, token_type  : String
, expires_in  : Long
):
  def toCachedToken(now: Instant): CachedAccessToken =
    CachedAccessToken(
      authorizationHeader = s"$token_type $access_token"
    , expiresAt           = now.plusSeconds(expires_in)
    )

private object ServiceNowAccessToken:
  given Reads[ServiceNowAccessToken] =
    Json.reads[ServiceNowAccessToken]
