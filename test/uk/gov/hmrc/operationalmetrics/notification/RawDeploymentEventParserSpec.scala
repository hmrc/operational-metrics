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

package uk.gov.hmrc.operationalmetrics.notification

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsResult, JsSuccess, Json}
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType
import uk.gov.hmrc.operationalmetrics.model.{CommitId, DeploymentConfigFile, DeploymentEvent, Environment, FileName, RepoName, ServiceName, UserName, Version}

import java.time.Instant

class RawDeploymentEventParserSpec extends AnyWordSpec with Matchers:

  private val baseJson =
    """{
      |  "deployer_name"                  : "deploy-with-docktor",
      |  "cloudformation_template_version": "v0.0.79",
      |  "microservice"                   : "common-transit-convention-traders",
      |  "mdtp_zone"                      : "protected",
      |  "microservice_version"           : "0.82.0",
      |  "lambda_version"                 : "v0.1.269",
      |  "lambda_name"                    : "ecs-mdtp-deployer-protected",
      |  "deployer_principal"             : "jenkins-orchestrator",
      |  "deployer_version"               : "0.186.0",
      |  "slug_uri"                       : "https://webstore.tax.service.gov.uk/slugs/common-transit-convention-traders/common-transit-convention-traders_0.82.0_0.5.2.tgz",
      |  "event_type"                     : "deployment-complete",
      |  "event_date_time"                : "2020-05-21T12:36:23.953Z",
      |  "deployment_id"                  : "f8a886f0-5d5c-11ea-8a93-02949b77ae4a",
      |  "environment"                    : "development",
      |  "config.0.repoName"              : "app-config-common",
      |  "config.0.gitSha"                : "1234",
      |  "config.0.fileName"              : "development-microservice-common.yaml",
      |  "config.1.repoName"              : "app-config-development",
      |  "config.1.gitSha"                : "5678",
      |  "config.1.fileName"              : "common-transit-convention-traders.yaml"
      |}""".stripMargin

  private val baseEvent: DeploymentEvent =
    DeploymentEvent(
      serviceName  = ServiceName("common-transit-convention-traders")
    , environment  = Environment.Development
    , deploymentId = "f8a886f0-5d5c-11ea-8a93-02949b77ae4a"
    , eventType    = ECSEventType.DeploymentComplete
    , version      = Version("0.82.0")
    , time         = Instant.parse("2020-05-21T12:36:23.953Z")
    , userName     = UserName("jenkins-orchestrator")
    , slugUri      = "https://webstore.tax.service.gov.uk/slugs/common-transit-convention-traders/common-transit-convention-traders_0.82.0_0.5.2.tgz"
    , messageId    = "" // message id not added yet
    , config       = DeploymentConfigFile(
                       repoName = RepoName("app-config-common")
                     , fileName = FileName("development-microservice-common.yaml")
                     , commitId = CommitId("1234")
                     ) ::
                     DeploymentConfigFile(
                       repoName = RepoName("app-config-development")
                     , fileName = FileName("common-transit-convention-traders.yaml")
                     , commitId = CommitId("5678")
                     ) :: Nil
    )

  private def parseJson(eventType: String): JsResult[DeploymentEvent] =
    Json.parse(baseJson.replace("\"deployment-complete\"", s"\"$eventType\""))
      .validate(RawDeploymentEventParser.deploymentEventReads)

  "deploymentEventReads" should:
    "parse a deployment-complete event" in:
      parseJson("deployment-complete")   shouldBe JsSuccess(baseEvent.copy(eventType = ECSEventType.DeploymentComplete))

    "parse an undeployment-complete event" in:
      parseJson("undeployment-complete") shouldBe JsSuccess(baseEvent.copy(eventType = ECSEventType.UnDeploymentComplete))

    "parse an undeployment-failed event" in:
      parseJson("undeployment-failed")   shouldBe JsSuccess(baseEvent.copy(eventType = ECSEventType.UnDeploymentFailed))
