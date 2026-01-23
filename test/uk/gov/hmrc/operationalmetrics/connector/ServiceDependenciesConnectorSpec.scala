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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.operationalmetrics.connector.ServiceDependenciesConnector.SlugInfo
import uk.gov.hmrc.operationalmetrics.model.{ServiceName, Version}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global

class ServiceDependenciesConnectorSpec
  extends AnyWordSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with HttpClientV2Support
    with WireMockSupport:

  private lazy val serviceDependenciesConnector: ServiceDependenciesConnector =
    ServiceDependenciesConnector(
      httpClientV2   = httpClientV2
    , servicesConfig = ServicesConfig(Configuration(
                         "microservice.services.service-dependencies.port" -> wireMockPort
                       , "microservice.services.service-dependencies.host" -> wireMockHost
                       ))
    )

  given HeaderCarrier = HeaderCarrier()

  "GET getSlugCreationDate" should:
    "return slug creation date for a service" in:
      stubFor(
        get(urlEqualTo("/api/sluginfo?name=service-1&version=1.57.0"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                """
                  {
                    "name": "service-1",
                    "version": "1.57.0",
                    "created" : "2019-01-20T14:09:46Z"
                  }
                """
              )
          )
      )

      val response: SlugInfo =
        serviceDependenciesConnector
          .getSlugCreationDate(ServiceName("service-1"), Version("1.57.0"))
          .futureValue

      response shouldBe
        SlugInfo(ServiceName("service-1"), Version("1.57.0"), Instant.parse("2019-01-20T14:09:46Z"))
