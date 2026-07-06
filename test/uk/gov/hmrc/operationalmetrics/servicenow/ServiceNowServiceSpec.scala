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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.operationalmetrics.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.operationalmetrics.connector.TeamsAndRepositoriesConnector.GitRepository
import uk.gov.hmrc.operationalmetrics.persistence.ServiceNowMappingsRepository
import uk.gov.hmrc.operationalmetrics.persistence.ServiceNowMappingsRepository.ServiceNowMapping

import scala.concurrent.{ExecutionContext, Future}

class ServiceNowServiceSpec
  extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with MockitoSugar:

  given HeaderCarrier = HeaderCarrier()
  import scala.concurrent.ExecutionContext.Implicits.global

  "ServiceNowService.updateServiceNowMappings" should:
    "store ServiceNow mappings parsed from repository.yaml text" in:
      val teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector =
        mock[TeamsAndRepositoriesConnector]
      val serviceNowMappingsRepository: ServiceNowMappingsRepository =
        mock[ServiceNowMappingsRepository]
      val service =
        ServiceNowService(
          teamsAndRepositoriesConnector
        , serviceNowMappingsRepository
        )

      when(teamsAndRepositoriesConnector.getAllServiceRepos()(using any[HeaderCarrier]))
        .thenReturn(Future.successful(
          Seq(
            GitRepository("service-1", Some("serviceNowMapping: service-now-mapping-1"))
          , GitRepository("service-2", Some("serviceNowMapping: '  service-now-mapping-2  '"))
          , GitRepository("service-3", Some("repoVisibility: public"))
          , GitRepository("service-4", Some("serviceNowMapping: ''"))
          , GitRepository("service-5", Some("not valid: ["))
          , GitRepository("service-6", None)
          )
        ))
      when(serviceNowMappingsRepository.putAll(any[Seq[ServiceNowMapping]])(using any[ExecutionContext]))
        .thenReturn(Future.unit)

      service.updateServiceNowMappings().futureValue

      verify(serviceNowMappingsRepository).putAll(eqTo(Seq(
        ServiceNowMapping("service-1", "service-now-mapping-1")
      , ServiceNowMapping("service-2", "service-now-mapping-2")
      )))(using any[ExecutionContext])
