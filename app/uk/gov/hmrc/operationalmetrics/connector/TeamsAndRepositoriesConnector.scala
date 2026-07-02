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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.operationalmetrics.connector.TeamsAndRepositoriesConnector.GitRepository
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

@Singleton
class TeamsAndRepositoriesConnector @Inject()(
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(using
  ec: ExecutionContext
):

  import uk.gov.hmrc.http.HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("teams-and-repositories")

  def getAllServiceRepos()(using HeaderCarrier): Future[Seq[GitRepository]] =
    httpClientV2
      .get(url"$url/api/v2/repositories?repoType=Service")
      .execute[Seq[GitRepository]]


object TeamsAndRepositoriesConnector:
  case class GitRepository(
    name              : String
  , repositoryYamlText: Option[String]
  )

  given reads: Reads[GitRepository] =
    ( (__ \ "name"              ).read[String]
    ~ (__ \ "repositoryYamlText").readNullable[String]
    )(GitRepository.apply)
