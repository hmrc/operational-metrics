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
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.{HttpClientV2Support, WireMockSupport}
import uk.gov.hmrc.operationalmetrics.connector.TeamsAndRepositoriesConnector.GitRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class TeamsAndRepositoriesConnectorSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with HttpClientV2Support
    with WireMockSupport:

  private lazy val teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector =
    TeamsAndRepositoriesConnector(
      httpClientV2   = httpClientV2
    , servicesConfig = ServicesConfig(Configuration(
                        "microservice.services.teams-and-repositories.port" -> wireMockPort
                      , "microservice.services.teams-and-repositories.host" -> wireMockHost
                      ))
    )

  given HeaderCarrier = HeaderCarrier()

  "GET getAllServiceRepos" should:
    "return service repositories with repository.yaml text when present" in:
      stubFor(
        get(urlEqualTo("/api/v2/repositories?repoType=Service"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                """[
                  |  {
                  |    "name": "service-1",
                  |    "repositoryYamlText": "serviceNowMapping: service-now-mapping-1"
                  |  },
                  |  {
                  |    "name": "service-2"
                  |  }
                  |]""".stripMargin
              )
          )
      )

      teamsAndRepositoriesConnector.getAllServiceRepos().futureValue shouldBe
        Seq(
          GitRepository("service-1", Some("serviceNowMapping: service-now-mapping-1"))
        , GitRepository("service-2", None)
        )
