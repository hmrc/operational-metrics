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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.operationalmetrics.servicenow.model.ServiceNowEvent

import java.time.{Clock, Duration, Instant, ZoneId, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global

class ServiceNowConnectorSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with HttpClientV2Support
    with WireMockSupport:

  private val changeRegistrationPath =
    "/api/ukrc/hmrc_change_registration/change_registration"

  private val fixedInstant =
    Instant.parse("2026-01-01T00:00:00Z")

  private def config =
    Configuration.from(Map(
      "servicenow.url"                 -> wireMockUrl
    , "servicenow.oauth.client-id"     -> "test-client-id"
    , "servicenow.oauth.client-secret" -> "test-client-secret"
    , "servicenow.oauth.expiry-buffer" -> "1.minute"
    )).withFallback(Configuration(ConfigFactory.load()))

  private def serviceNowConnector(
    clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
  ): ServiceNowConnector =
    ServiceNowConnector(httpClientV2, config, clock)

  given HeaderCarrier = HeaderCarrier()

  private val serviceNowEvent =
    ServiceNowEvent(
      shortDescription     = "service-1 1.0.0 deployed in Production"
    , description          = "Pipeline execution ID: 123\nRepository: https://github.com/hmrc/service-1"
    , cmdbCI               = "service-now-mapping-1"
    , workStart            = Instant.parse("2026-05-15T15:24:19Z")
    , workEnd              = Instant.parse("2026-05-15T16:24:19Z")
    , correlationId        = "edge-123"
    )

  "ServiceNowEvent JSON" should:
    "write the ServiceNow payload as a single-element array with expected field names" in:
      val json = Json.toJson(serviceNowEvent)
      val payload: play.api.libs.json.JsValue =
        json match
          case play.api.libs.json.JsArray(values) if values.size == 1 => values.head
          case other                                                  => fail(s"Expected single-element array, got $other")

      (payload \ "short_description"  ).as[String] shouldBe "service-1 1.0.0 deployed in Production"
      (payload \ "description"        ).as[String] shouldBe "Pipeline execution ID: 123\nRepository: https://github.com/hmrc/service-1"
      (payload \ "cmdb_ci"            ).as[String] shouldBe "service-now-mapping-1"
      (payload \ "work_start"         ).as[String] shouldBe "15/05/2026 16:24:19"
      (payload \ "work_end"           ).as[String] shouldBe "15/05/2026 17:24:19"
      (payload \ "close_code"         ).as[String] shouldBe "successful"
      (payload \ "correlation_id"     ).as[String] shouldBe "edge-123"
      (payload \ "correlation_display").as[String] shouldBe "MDTP"
      (payload \ "repository"         ).toOption shouldBe None
      (payload \ "commitIds"          ).toOption shouldBe None
      (payload \ "requestedBy"        ).toOption shouldBe None
      (payload \ "assignmentGroup"    ).toOption shouldBe None

  "POST sendToServiceNow" should:
    "request an OAuth token and send the event when ServiceNow responds with 201" in:
      stubToken()
      stubChangeRegistration()

      serviceNowConnector().sendToServiceNow(serviceNowEvent).futureValue shouldBe ()

      verifyTokenRequested(1)

    "fail when ServiceNow responds with an unexpected 2xx status" in:
      stubToken()
      stubChangeRegistration(status = 200)

      serviceNowConnector().sendToServiceNow(serviceNowEvent).failed.futureValue.getMessage should
        include("Unexpected response from ServiceNow: 200")

    "fail when ServiceNow responds with a 5xx error" in:
      stubToken()
      stubChangeRegistration(status = 500, body = "Internal Server Error")

      serviceNowConnector().sendToServiceNow(serviceNowEvent).failed.futureValue.getMessage should
        include("500")

    "fail when ServiceNow responds with a 4xx error" in:
      stubToken()
      stubChangeRegistration(status = 400, body = "Bad Request")

      serviceNowConnector().sendToServiceNow(serviceNowEvent).failed.futureValue.getMessage should
        include("400")

    "reuse the current OAuth token while it is still valid" in:
      stubToken(accessToken = "cached-token")
      stubChangeRegistration(authorization = "Bearer cached-token")

      val connector = serviceNowConnector()

      connector.sendToServiceNow(serviceNowEvent).futureValue shouldBe ()
      connector.sendToServiceNow(serviceNowEvent).futureValue shouldBe ()

      verifyTokenRequested(1)
      verify(
        2,
        postRequestedFor(urlEqualTo(changeRegistrationPath))
          .withHeader("Authorization", equalTo("Bearer cached-token"))
      )

    "refresh the OAuth token when it reaches the expiry buffer" in:
      val clock     = MutableClock(fixedInstant)
      val connector = serviceNowConnector(clock)

      stubToken(accessToken = "first-token", expiresIn = 120)
      stubChangeRegistration(authorization = "Bearer first-token")

      connector.sendToServiceNow(serviceNowEvent).futureValue shouldBe ()

      clock.advance(Duration.ofSeconds(61))
      stubToken(accessToken = "second-token", expiresIn = 120)
      stubChangeRegistration(authorization = "Bearer second-token")

      connector.sendToServiceNow(serviceNowEvent).futureValue shouldBe ()

      verifyTokenRequested(2)

    "fail when the OAuth token request fails" in:
      stubFor(
        post(urlEqualTo("/oauth_token.do"))
          .willReturn(
            aResponse()
              .withStatus(500)
              .withBody("Internal Server Error")
          )
      )

      serviceNowConnector().sendToServiceNow(serviceNowEvent).failed.futureValue.getMessage should
        include("ServiceNow token upstream error: 500")

  private def stubToken(
    accessToken: String = "test-token"
  , expiresIn  : Int    = 1799
  ): Unit =
    stubFor(
      post(urlEqualTo("/oauth_token.do"))
        .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
        .withRequestBody(containing("grant_type=client_credentials"))
        .withRequestBody(containing("client_id=test-client-id"))
        .withRequestBody(containing("client_secret=test-client-secret"))
        .willReturn(
          okJson(
            s"""{
               |  "access_token": "$accessToken",
               |  "scope": "hmrc_chne_regis",
               |  "token_type": "Bearer",
               |  "expires_in": $expiresIn
               |}""".stripMargin
          )
        )
    )

  private def stubChangeRegistration(
    authorization: String = "Bearer test-token"
  , status       : Int    = 201
  , body         : String = ""
  ): Unit =
    stubFor(
      post(urlEqualTo(changeRegistrationPath))
        .withHeader("Authorization", equalTo(authorization))
        .withRequestBody(equalToJson(Json.toJson(serviceNowEvent).toString))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(body)
        )
    )

  private def verifyTokenRequested(times: Int): Unit =
    verify(
      times,
      postRequestedFor(urlEqualTo("/oauth_token.do"))
        .withRequestBody(containing("grant_type=client_credentials"))
        .withRequestBody(containing("client_id=test-client-id"))
        .withRequestBody(containing("client_secret=test-client-secret"))
    )

  private final case class MutableClock(
    private var current: Instant
  , zone               : ZoneId = ZoneOffset.UTC
  ) extends Clock:
    override def getZone: ZoneId =
      zone

    override def withZone(zone: ZoneId): Clock =
      copy(zone = zone)

    override def instant(): Instant =
      current

    def advance(duration: Duration): Unit =
      current = current.plus(duration)
