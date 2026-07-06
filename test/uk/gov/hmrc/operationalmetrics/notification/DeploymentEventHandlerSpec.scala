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
import play.api.Configuration
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType
import uk.gov.hmrc.operationalmetrics.model.{DeploymentEvent, Environment, ServiceName, UserName, Version}

import java.time.Instant

class DeploymentEventHandlerSpec extends AnyWordSpec with Matchers:

  "DeploymentEventHandler.AllowList" should:
    "default to production events for all services" in:
      val allowList =
        DeploymentEventHandler.AllowList.fromConfig(Configuration.from(Map.empty))

      allowList.allows(deploymentEvent(Environment.Production, ServiceName("service-1"))) shouldBe true
      allowList.allows(deploymentEvent(Environment.QA        , ServiceName("service-1"))) shouldBe false
      allowList.allows(deploymentEvent(Environment.Production, ServiceName("service-2"))) shouldBe true

    "allow only configured environments and services when populated" in:
      val allowList =
        DeploymentEventHandler.AllowList.fromConfig(Configuration(
          "deployment-event-handler.allow-list.environments" -> Seq("qa", "staging")
        , "deployment-event-handler.allow-list.services"     -> Seq("service-1")
        ))

      allowList.allows(deploymentEvent(Environment.QA        , ServiceName("service-1"))) shouldBe true
      allowList.allows(deploymentEvent(Environment.Staging   , ServiceName("service-1"))) shouldBe true
      allowList.allows(deploymentEvent(Environment.Production, ServiceName("service-1"))) shouldBe false
      allowList.allows(deploymentEvent(Environment.QA        , ServiceName("service-2"))) shouldBe false

    "treat an empty allow-list as all values for that dimension" in:
      val allowList =
        DeploymentEventHandler.AllowList.fromConfig(Configuration(
          "deployment-event-handler.allow-list.environments" -> Seq.empty[String]
        , "deployment-event-handler.allow-list.services"     -> Seq("service-1")
        ))

      allowList.allows(deploymentEvent(Environment.Production, ServiceName("service-1"))) shouldBe true
      allowList.allows(deploymentEvent(Environment.QA        , ServiceName("service-1"))) shouldBe true
      allowList.allows(deploymentEvent(Environment.QA        , ServiceName("service-2"))) shouldBe false

    "normalise configured values before matching" in:
      val allowList =
        DeploymentEventHandler.AllowList.fromConfig(Configuration(
          "deployment-event-handler.allow-list.environments" -> Seq(" Production ")
        , "deployment-event-handler.allow-list.services"     -> Seq(" SERVICE-1 ")
        ))

      allowList.allows(deploymentEvent(Environment.Production, ServiceName("service-1"))) shouldBe true

  private def deploymentEvent(
    environment: Environment
  , serviceName: ServiceName
  ): DeploymentEvent =
    DeploymentEvent(
      serviceName  = serviceName
    , environment  = environment
    , deploymentId = "deployment-id"
    , eventType    = ECSEventType.DeploymentComplete
    , version      = Version("1.0.0")
    , time         = Instant.parse("2026-01-01T00:00:00Z")
    , userName     = UserName("user-1")
    , config       = Seq.empty
    , slugUri      = "slug-uri"
    , messageId    = "message-id"
    )
