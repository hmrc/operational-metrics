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

package uk.gov.hmrc.operationalmetrics.service

import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.{eq as eqTo, *}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.operationalmetrics.connector.{ReleasesConnector, ServiceDependenciesConnector}
import uk.gov.hmrc.operationalmetrics.connector.ReleasesConnector.{DeploymentEvent, WhatsRunningWhereVersion}
import uk.gov.hmrc.operationalmetrics.connector.ReleasesConnector.WhatsRunningWhere.WhatsRunningWhere
import uk.gov.hmrc.operationalmetrics.connector.ServiceDependenciesConnector.SlugInfo
import uk.gov.hmrc.operationalmetrics.model.{Environment, LeadTimeMeasurement, ServiceLeadTimes, ServiceName, Version}
import uk.gov.hmrc.operationalmetrics.persistence.ServiceLeadTimesRepository

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class DoraMetricsServiceSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar:

  "DoraMetricsService.updateServiceLeadTimes" should:
    "calculate lead times for latest releases and save them to repository" in new DoraMetricsServiceFixture:
      service.updateServiceLeadTimes().futureValue

      verify(mockReleasesConnector           , times(1)).releases()
      verify(mockServiceDependenciesConnector, times(1)).getSlugCreationDate(ServiceName("service-1"), Version("1.57.0"))
      verify(mockReleasesConnector           , times(1)).firstCompletedDeployment(ServiceName("service-1"), Version("1.57.0"), Environment.Production)
      verify(mockServiceLeadTimesRepository  , times(1)).putAll(Seq(leadTimes))

  private abstract class DoraMetricsServiceFixture extends MockitoSugar:
    protected val mockReleasesConnector           : ReleasesConnector            = mock[ReleasesConnector]
    protected val mockServiceDependenciesConnector: ServiceDependenciesConnector = mock[ServiceDependenciesConnector]
    protected val mockServiceLeadTimesRepository  : ServiceLeadTimesRepository   = mock[ServiceLeadTimesRepository]

    protected val service: DoraMetricsService =
      DoraMetricsService(
        mockReleasesConnector
      , mockServiceDependenciesConnector
      , mockServiceLeadTimesRepository
      )

    given HeaderCarrier    = HeaderCarrier()
    given ExecutionContext = scala.concurrent.ExecutionContext.global

    private val created : Instant = Instant.parse("2026-01-07T11:51:23.000Z")
    private val deployed: Instant = Instant.parse("2026-01-09T11:51:23.000Z")

    protected val leadTimes: ServiceLeadTimes =
      ServiceLeadTimes(
        serviceName = ServiceName("service-1")
      , leadTimes   = Seq(LeadTimeMeasurement(environment = Environment.Production, version = Version("1.57.0"), slugCreatedAt = created, firstDeployedAt = deployed, days = 2))
      )

    private val releases: Seq[WhatsRunningWhere] =
      WhatsRunningWhere(ServiceName("service-1"), List(WhatsRunningWhereVersion(Environment.Production, Version("1.57.0")))) ::
      Nil

    private val slugInfo: SlugInfo =
      SlugInfo(ServiceName("service-1"), Version("1.57.0"), created)

    private val deploymentEvent: DeploymentEvent =
      DeploymentEvent(ServiceName("service-1"), Version("1.57.0"), deployed)

    when(mockReleasesConnector.releases()(using any[HeaderCarrier]))
      .thenReturn(Future.successful(releases))

    when(mockServiceDependenciesConnector.getSlugCreationDate(any[ServiceName], any[Version])(using any[HeaderCarrier]))
      .thenReturn(Future.successful(slugInfo))

    when(mockReleasesConnector.firstCompletedDeployment(any[ServiceName], any[Version], eqTo(Environment.Production))(using any[HeaderCarrier]))
      .thenReturn(Future.successful(deploymentEvent))

    when(mockServiceLeadTimesRepository.putAll(any[Seq[ServiceLeadTimes]])(using any[ExecutionContext]))
        .thenReturn(Future.unit)
