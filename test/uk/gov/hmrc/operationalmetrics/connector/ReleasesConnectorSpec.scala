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

package uk.gov.hmrc.operationalmetrics.connector


import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.operationalmetrics.connector.ReleasesConnector.WhatsRunningWhere.WhatsRunningWhere
import uk.gov.hmrc.operationalmetrics.connector.ReleasesConnector.{HistoricDeployment, WhatsRunningWhereVersion}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.operationalmetrics.model.{Environment, ServiceName, Version}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class ReleasesConnectorSpec
  extends AnyWordSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with HttpClientV2Support
    with WireMockSupport:

  private lazy val releasesConnector: ReleasesConnector =
    ReleasesConnector(
      httpClientV2   = httpClientV2
    , servicesConfig = ServicesConfig(Configuration(
                         "microservice.services.releases-api.port" -> wireMockPort
                       , "microservice.services.releases-api.host" -> wireMockHost
                       ))
    )

  given HeaderCarrier = HeaderCarrier()

  "GET releases" should:
    "return what's running where" in:
      stubFor(
        get(urlEqualTo("/releases-api/whats-running-where"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                """[
                    {
                      "applicationName": "service-1",
                      "versions": [
                        {
                          "environment": "production",
                          "versionNumber": "1.57.0",
                          "lastDeployed": "2019-05-29T14:09:48Z",
                          "config": []
                        }
                      ]
                    },
                    {
                      "applicationName": "service-2",
                      "versions": [
                        {
                          "environment": "staging",
                          "versionNumber": "0.44.0",
                          "lastDeployed": "2019-05-29T14:09:46Z",
                          "config": []
                        },
                        {
                          "environment": "production",
                          "versionNumber": "0.44.0",
                          "lastDeployed": "2019-04-29T14:09:48Z",
                          "config": []
                        }
                      ]
                    }
                ]"""
              )
          )
      )

      val response: Seq[WhatsRunningWhere] =
        releasesConnector
          .releases()
          .futureValue

      response shouldBe
        WhatsRunningWhere(ServiceName("service-1"), List(WhatsRunningWhereVersion(Environment.Production, Version("1.57.0")))) ::
        WhatsRunningWhere(ServiceName("service-2"), List(WhatsRunningWhereVersion(Environment.Staging   , Version("0.44.0")), WhatsRunningWhereVersion(Environment.Production, Version("0.44.0")))) ::
        Nil

  "GET firstCompletedDeployment" should:
    "return the first completed deployment event for the given service, version and environment" in:
      stubFor(
        get(urlEqualTo("/releases-api/firstDeployment?service=service-1&version=1.57.0&environment=production"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                """
                  {
                    "serviceName": "service-1",
                    "version": "1.57.0",
                    "time" : "2019-04-20T14:09:46Z"
                  }
                """
              )
          )
      )

      val response: Option[HistoricDeployment] =
        releasesConnector
          .firstCompletedDeployment(ServiceName("service-1"), Version("1.57.0"), Environment.Production)
          .futureValue

      response shouldBe
        Some(HistoricDeployment(ServiceName("service-1"), Version("1.57.0"), Instant.parse("2019-04-20T14:09:46Z")))

    "return none when deployment event for the given service, version and environment is not found" in:
      stubFor(
        get(urlEqualTo("/releases-api/firstDeployment?service=service-1&version=1.57.0&environment=production"))
          .willReturn(
            aResponse()
              .withStatus(404)
          )
        )

      val response: Option[HistoricDeployment] =
        releasesConnector
          .firstCompletedDeployment(ServiceName("service-1"), Version("1.57.0"), Environment.Production)
          .futureValue

      response shouldBe None

  "GET previousDeployment" should:
    "return the previous deployment based on deployment timestamp for given a service and environment" in:
      stubFor(
        get(urlEqualTo("/releases-api/previousDeployment?service=service-1&environment=production&time=2026-04-29T14:19:00.337Z"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                """
                  {
                    "serviceName": "service-1",
                    "version": "1.57.0",
                    "time" : "2026-04-25T11:19:00.337Z"
                  }
                """
              )
          )
      )

      val response: Option[HistoricDeployment] =
        releasesConnector
          .previousDeployment(ServiceName("service-1"), Environment.Production, Instant.parse("2026-04-29T14:19:00.337Z"))
          .futureValue

      response shouldBe
        Some(HistoricDeployment(ServiceName("service-1"), Version("1.57.0"), Instant.parse("2026-04-25T11:19:00.337Z")))
    
    "return none when previous deployment based on the deployment timestamp for the given service and environment is not found" in:
      stubFor(
        get(urlEqualTo("/releases-api/previousDeployment?service=service-1&environment=production&time=2026-04-29T14:19:00.337Z"))
          .willReturn(
            aResponse()
              .withStatus(404)
          )
        )

      val response: Option[HistoricDeployment] =
        releasesConnector
          .previousDeployment(ServiceName("service-1"), Environment.Production, Instant.parse("2026-04-29T14:19:00.337Z"))
          .futureValue

      response shouldBe None
