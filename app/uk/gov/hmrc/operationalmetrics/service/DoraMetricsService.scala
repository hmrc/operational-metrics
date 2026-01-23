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

import cats.implicits.*
import uk.gov.hmrc.operationalmetrics.connector.{ReleasesConnector, ServiceDependenciesConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.operationalmetrics.connector.ReleasesConnector.DeploymentEvent
import uk.gov.hmrc.operationalmetrics.connector.ServiceDependenciesConnector.SlugInfo
import uk.gov.hmrc.operationalmetrics.model.{Environment, LeadTimeMeasurement, ServiceLeadTimes, ServiceName, Version}
import uk.gov.hmrc.operationalmetrics.persistence.ServiceLeadTimesRepository

import java.time.Duration
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DoraMetricsService @Inject() (
  releasesConnector           : ReleasesConnector
, serviceDependenciesConnector: ServiceDependenciesConnector
, serviceLeadTimesRepository  : ServiceLeadTimesRepository
)(using
  ec: ExecutionContext
):

  def getServiceLeadTimes(): Future[Seq[ServiceLeadTimes]] =
    serviceLeadTimesRepository.findAll()
  
  def updateServiceLeadTimes()(using HeaderCarrier): Future[Unit] =
    for
      releases          <- releasesConnector.releases()
      prodReleases      =  releases.flatMap: wrw =>
                             wrw.versions.collectFirst:
                              case v if v.environment == Environment.Production =>
                                wrw.serviceName -> v.version
      slugCreationDates <- prodReleases.foldLeftM(Map.empty[ServiceName, SlugInfo]):
                             case (acc, (service, version)) =>
                               serviceDependenciesConnector
                                 .getSlugCreationDate(service, version)
                                 .map: slugInfo =>
                                   acc + (service -> slugInfo)
      prodDeployments   <- prodReleases.foldLeftM(Map.empty[ServiceName, DeploymentEvent]):
                             case (acc, (service, version)) =>
                               releasesConnector
                                 .firstCompletedDeployment(service, version, Environment.Production)
                                 .map: event =>
                                   acc + (service -> event)
      leadTimes         =  prodReleases.map: (service, version) =>
                             val slugCreationDate    = slugCreationDates(service).created
                             val firstDeploymentDate = prodDeployments(service).time
                             val days                = Duration.between(slugCreationDate, firstDeploymentDate).toHours.toInt / 24
                             ServiceLeadTimes(
                               serviceName = service
                             , leadTimes   = Seq(
                                               LeadTimeMeasurement(
                                                 environment     = Environment.Production
                                               , version         = version
                                               , slugCreatedAt   = slugCreationDate
                                               , firstDeployedAt = firstDeploymentDate
                                               , days            = days
                                               )
                                             )
                             )
      _                 <- serviceLeadTimesRepository.putAll(leadTimes)                            
    yield ()
