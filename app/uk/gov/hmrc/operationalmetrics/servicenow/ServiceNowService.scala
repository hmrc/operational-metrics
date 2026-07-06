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

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.operationalmetrics.connector.TeamsAndRepositoriesConnector
import uk.gov.hmrc.operationalmetrics.connector.TeamsAndRepositoriesConnector.GitRepository
import uk.gov.hmrc.operationalmetrics.persistence.ServiceNowMappingsRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import org.yaml.snakeyaml.Yaml
import play.api.Logging
import uk.gov.hmrc.operationalmetrics.persistence.ServiceNowMappingsRepository.ServiceNowMapping

@Singleton
class ServiceNowService @Inject() (
  teamsAndRepositoriesConnector: TeamsAndRepositoriesConnector
, serviceNowMappingsRepository : ServiceNowMappingsRepository
)(using
  ec: ExecutionContext
) extends Logging:

  private val serviceNowMappingKey: String =
    "serviceNowMapping"

  private def toMappings(repos: Seq[GitRepository]): Seq[ServiceNowMapping] =
    repos.flatMap: repo =>
      serviceNowMapping(repo).map(v => ServiceNowMapping(repo.name, v))

  private def serviceNowMapping(repo: GitRepository): Option[String] =
    repo.repositoryYamlText
      .flatMap(text => scala.util.Try(Option(new Yaml().load[AnyRef](text))).toOption.flatten)
      .collect { case m: java.util.Map[?, ?] @unchecked => m }
      .flatMap(m => Option(m.get(serviceNowMappingKey)))
      .map(_.toString)
      .map(_.trim)
      .filter(_.nonEmpty)
  
  def updateServiceNowMappings()(using HeaderCarrier): Future[Unit] =
    for
      repos    <- teamsAndRepositoriesConnector.getAllServiceRepos()
      _        =  logger.info(s"fetched ${repos.size} repos")
      mappings =  toMappings(repos)
      _        =  logger.info(s"built ${mappings.size} mappings")
      _        <- serviceNowMappingsRepository.putAll(mappings)
      _        =  logger.info("putAll completed")
    yield ()
