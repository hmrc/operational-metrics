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
import uk.gov.hmrc.operationalmetrics.connector.ArtefactProcessorConnector.MetaArtefact
import uk.gov.hmrc.operationalmetrics.model.Version
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class ArtefactProcessorConnectorSpec
  extends AnyWordSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with IntegrationPatience
    with HttpClientV2Support
    with WireMockSupport:

  private lazy val artefactProcessorConnector: ArtefactProcessorConnector =
    ArtefactProcessorConnector(
      httpClientV2   = httpClientV2
    , servicesConfig = ServicesConfig(Configuration(
                        "microservice.services.artefact-processor.port" -> wireMockPort
                      , "microservice.services.artefact-processor.host" -> wireMockHost
                      ))
    )

  given HeaderCarrier = HeaderCarrier()

  "GET metaArtefact" should:
    "return meta-artefact for a repo name and version" in:
      stubFor(
        get(urlEqualTo("/result/meta/repo-1/1.0.0"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(
                """{
                 "name"              : "repo-1",
                 "version"           : "1.0.0",
                 "uri"               : "https://store/meta/my-meta/repo-1-v1.0.0.meta.tgz",
                 "gitUrl"            : "https://github.com/hmrc/repo-1.git",
                 "dependencyDotBuild": "dependencyDotBuild",
                 "buildInfo"         : { "GIT_BRANCH": "origin/main", "GIT_COMMIT": "123abc"},
                 "modules"           : [
                   { "name"                 : "module-1",
                     "group"                : "uk.gov.hmrc",
                     "sbtVersion"           : "1.4.9",
                     "crossScalaVersions"   : [ "2.12.14" ],
                     "publishSkip"          : false,
                     "dependencyDotCompile" : "dependencyDotCompile",
                     "dependencyDotProvided": "dependencyDotProvided",
                     "dependencyDotTest"    : "dependencyDotTest",
                     "dependencyDotIt"      : "dependencyDotIt",
                     "aggregates"           : [],
                     "root"                 : true
                   }
                 ],
                 "created"           : "2022-01-04T17:46:18.588Z",
                 "lastProcessed"     : "2022-01-05T17:46:18.588Z"
              }"""
              )
          )
      )

      val response: Option[MetaArtefact] =
        artefactProcessorConnector
          .getMetaArtefact("repo-1", Version(1, 0, 0, "1.0.0"))
          .futureValue

      response shouldBe
        Some(MetaArtefact("repo-1", Some("https://github.com/hmrc/repo-1.git"), Map("GIT_BRANCH" -> "origin/main", "GIT_COMMIT" -> "123abc")))

    "return none when no meta-artefact found for a repo name and version" in:
      stubFor(
        get(urlEqualTo("/result/meta/repo-1/1.0.0"))
          .willReturn(
            aResponse()
              .withStatus(404)
          )
        )

      val response: Option[MetaArtefact] =
        artefactProcessorConnector
          .getMetaArtefact("repo-1", Version(1, 0, 0, "1.0.0"))
          .futureValue

      response shouldBe None
