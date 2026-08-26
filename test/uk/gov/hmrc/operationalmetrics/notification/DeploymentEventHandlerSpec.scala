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

  "DeploymentEventHandler.EventFilter" should:
    "default to production events for all services" in:
      val eventFilter =
        DeploymentEventHandler.EventFilter.fromConfig(Configuration.from(Map.empty))

      eventFilter.allows(deploymentEvent(Environment.Production, ServiceName("service-1"))) shouldBe true
      eventFilter.allows(deploymentEvent(Environment.QA        , ServiceName("service-1"))) shouldBe false
      eventFilter.allows(deploymentEvent(Environment.Production, ServiceName("service-2"))) shouldBe true

    "allow only configured environments while rejecting denied services" in:
      val eventFilter =
        DeploymentEventHandler.EventFilter.fromConfig(Configuration(
          "deployment-event-handler.allow-list.environments" -> Seq("qa", "staging")
        , "deployment-event-handler.deny-list.services"      -> Seq("service-2", "object-store")
        ))

      eventFilter.allows(deploymentEvent(Environment.QA        , ServiceName("service-1")))    shouldBe true
      eventFilter.allows(deploymentEvent(Environment.Staging   , ServiceName("service-1")))    shouldBe true
      eventFilter.allows(deploymentEvent(Environment.Production, ServiceName("service-1")))    shouldBe false
      eventFilter.allows(deploymentEvent(Environment.QA        , ServiceName("service-2")))    shouldBe false
      eventFilter.allows(deploymentEvent(Environment.QA        , ServiceName("object-store"))) shouldBe false

    "honour the service allow-list when it is populated" in:
      val eventFilter =
        DeploymentEventHandler.EventFilter.fromConfig(Configuration(
          "deployment-event-handler.allow-list.environments" -> Seq("qa")
        , "deployment-event-handler.allow-list.services"     -> Seq("service-1")
        , "deployment-event-handler.deny-list.services"      -> Seq("object-store")
        ))

      eventFilter.allows(deploymentEvent(Environment.QA, ServiceName("service-1")))    shouldBe true
      eventFilter.allows(deploymentEvent(Environment.QA, ServiceName("service-2")))    shouldBe false
      eventFilter.allows(deploymentEvent(Environment.QA, ServiceName("object-store"))) shouldBe false

    "treat an empty environment allow-list as all environments and an empty service deny-list as no denied services" in:
      val eventFilter =
        DeploymentEventHandler.EventFilter.fromConfig(Configuration(
          "deployment-event-handler.allow-list.environments" -> Seq.empty[String]
        , "deployment-event-handler.allow-list.services"     -> Seq.empty[String]
        , "deployment-event-handler.deny-list.services"      -> Seq.empty[String]
        ))

      eventFilter.allows(deploymentEvent(Environment.Production, ServiceName("service-1"))) shouldBe true
      eventFilter.allows(deploymentEvent(Environment.QA        , ServiceName("service-1"))) shouldBe true
      eventFilter.allows(deploymentEvent(Environment.QA        , ServiceName("service-2"))) shouldBe true

    "normalise configured values before matching" in:
      val eventFilter =
        DeploymentEventHandler.EventFilter.fromConfig(Configuration(
          "deployment-event-handler.allow-list.environments" -> Seq(" Production ")
        , "deployment-event-handler.deny-list.services"      -> Seq(" OBJECT-STORE ")
        ))

      eventFilter.allows(deploymentEvent(Environment.Production, ServiceName("service-1")))    shouldBe true
      eventFilter.allows(deploymentEvent(Environment.Production, ServiceName("object-store"))) shouldBe false

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
