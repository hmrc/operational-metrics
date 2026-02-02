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

package uk.gov.hmrc.operationalmetrics.controllers

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.operationalmetrics.model.*
import uk.gov.hmrc.operationalmetrics.service.DoraMetricsService

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class DoraMetricsControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar:

  private val mockDoraMetricsService: DoraMetricsService = mock[DoraMetricsService]
  
  private val created : Instant = Instant.parse("2026-01-07T11:51:23.000Z")
  private val deployed: Instant = Instant.parse("2026-01-09T11:51:23.000Z")

  private val leadTimes1: ServiceLeadTimes =
    ServiceLeadTimes(
      serviceName = ServiceName("service-1")
    , leadTimes   = Seq(LeadTimeMeasurement(environment = Environment.Production, version = Version("1.57.0"), slugCreatedAt = created, firstDeployedAt = deployed, days = 2))
    )

  private val leadTimes2: ServiceLeadTimes =
    ServiceLeadTimes(
      serviceName = ServiceName("service-2")
    , leadTimes   = Seq(LeadTimeMeasurement(environment = Environment.Production, version = Version("0.44.0"), slugCreatedAt = created, firstDeployedAt = deployed, days = 2))
    )

  private val leadTimes3: ServiceLeadTimes =
    ServiceLeadTimes(
      serviceName = ServiceName("service-3")
    , leadTimes   = Seq(LeadTimeMeasurement(environment = Environment.Production, version = Version("1.0.0"), slugCreatedAt = created, firstDeployedAt = deployed, days = 2))
    )

  private val leadTimes: Seq[ServiceLeadTimes] = Seq(leadTimes1, leadTimes2, leadTimes3)

  private val json =
    """
      [
        {
          "serviceName": "service-1",
          "leadTimes": [
            {
              "environment": "production",
              "version": "1.57.0",
              "slugCreatedAt": "2026-01-07T11:51:23Z",
              "firstDeployedAt": "2026-01-09T11:51:23Z",
              "days": 2
            }
          ]
        },
        {
          "serviceName": "service-2",
          "leadTimes": [
            {
              "environment": "production",
              "version": "0.44.0",
              "slugCreatedAt": "2026-01-07T11:51:23Z",
              "firstDeployedAt": "2026-01-09T11:51:23Z",
              "days": 2
            }
          ]
        },
        {
          "serviceName": "service-3",
          "leadTimes": [
            {
              "environment": "production",
              "version": "1.0.0",
              "slugCreatedAt": "2026-01-07T11:51:23Z",
              "firstDeployedAt": "2026-01-09T11:51:23Z",
              "days": 2
            }
          ]
        }
      ]
    """

  "DoraMetricsController.serviceLeadTimes" should:
    "return all service lead times" in:
      when(mockDoraMetricsService.getServiceLeadTimes())
        .thenReturn(Future.successful(leadTimes))

      val controller = DoraMetricsController(mockDoraMetricsService, Helpers.stubControllerComponents())
      val result     = controller.serviceLeadTimes()(FakeRequest())
      val bodyText   = contentAsJson(result)
      bodyText mustBe Json.parse(json)  
