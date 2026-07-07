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
import uk.gov.hmrc.operationalmetrics.model.UserName
import uk.gov.hmrc.operationalmetrics.servicenow.model.ServiceNowEvent

import scala.concurrent.ExecutionContext.Implicits.global

class ServiceNowConnectorSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with HttpClientV2Support
    with WireMockSupport:

  private val config =
    Configuration.from(Map(
      "servicenow.url"           -> wireMockUrl
    , "servicenow.authorization" -> "Bearer test-token"
    )).withFallback(Configuration(ConfigFactory.load()))

  private lazy val serviceNowConnector: ServiceNowConnector =
    ServiceNowConnector(httpClientV2, config)

  given HeaderCarrier = HeaderCarrier()

  private val serviceNowEvent =
    ServiceNowEvent(
      requestedBy          = UserName("user-1")
    , shortDescription     = "service-1 1.0.0 deployed in Production"
    , description          = "Pipeline execution ID: 123\nRepository: https://github.com/hmrc/service-1"
    , cmdbCI               = "service-now-mapping-1"
    )

  "ServiceNowEvent JSON" should:
    "write description as a single string and omit the old top-level detail fields" in:
      val json = Json.toJson(serviceNowEvent)

      (json \ "description").as[String] shouldBe "Pipeline execution ID: 123\nRepository: https://github.com/hmrc/service-1"
      (json \ "cmdb_ci"    ).as[String] shouldBe "service-now-mapping-1"
      (json \ "repository" ).toOption shouldBe None
      (json \ "commitIds"  ).toOption shouldBe None

  "POST sendToServiceNow" should:
    "return unit when ServiceNow responds with 201" in:
      stubFor(
        post(urlEqualTo("/api"))
          .withHeader("Authorization", equalTo("Bearer test-token"))
          .withRequestBody(equalToJson(Json.toJson(serviceNowEvent).toString))
          .willReturn(
            aResponse()
              .withStatus(201)
          )
      )

      serviceNowConnector.sendToServiceNow(serviceNowEvent).futureValue shouldBe ()

    "fail when ServiceNow responds with an unexpected 2xx status" in:
      stubFor(
        post(urlEqualTo("/api"))
          .withRequestBody(equalToJson(Json.toJson(serviceNowEvent).toString))
          .willReturn(
            aResponse()
              .withStatus(200)
          )
      )

      serviceNowConnector.sendToServiceNow(serviceNowEvent).failed.futureValue.getMessage should
        include("Unexpected response from ServiceNow: 200")

    "fail when ServiceNow responds with a 5xx error" in:
      stubFor(
        post(urlEqualTo("/api"))
          .withRequestBody(equalToJson(Json.toJson(serviceNowEvent).toString))
          .willReturn(
            aResponse()
              .withStatus(500)
              .withBody("Internal Server Error")
          )
      )

      serviceNowConnector.sendToServiceNow(serviceNowEvent).failed.futureValue.getMessage should
        include("500")

    "fail when ServiceNow responds with a 4xx error" in:
      stubFor(
        post(urlEqualTo("/api"))
          .withRequestBody(equalToJson(Json.toJson(serviceNowEvent).toString))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withBody("Bad Request")
          )
      )

      serviceNowConnector.sendToServiceNow(serviceNowEvent).failed.futureValue.getMessage should
        include("400")
