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

import com.google.inject.{Inject, Singleton}
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Reads, __}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.operationalmetrics.connector.ArtefactProcessorConnector.MetaArtefact
import uk.gov.hmrc.operationalmetrics.model.{CommitId, Version}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ArtefactProcessorConnector @Inject()(
  httpClientV2: HttpClientV2
, servicesConfig: ServicesConfig
)(using 
  ec: ExecutionContext
):
  import HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("artefact-processor")

  def getMetaArtefact(
    repositoryName: String
  , version       : Version
  )(using hc: HeaderCarrier): Future[Option[MetaArtefact]] =
    given Reads[MetaArtefact] = MetaArtefact.reads
    httpClientV2
      .get(url"$url/result/meta/$repositoryName/${version.original}")
      .execute[Option[MetaArtefact]]

object ArtefactProcessorConnector:
  private object BuildInfo:
    val gitBranch = "GIT_BRANCH"
    val gitCommit = "GIT_COMMIT"

  case class MetaArtefact(
    name     : String
  , gitUrl   : Option[String]      = None
  , buildInfo: Map[String, String] = Map.empty
  ):
    def gitCommit: Option[CommitId] =
      buildInfo.get(BuildInfo.gitCommit).map(CommitId.apply)

    def gitBranch: Option[String] =
      buildInfo.get(BuildInfo.gitBranch)

  object MetaArtefact:
    given reads: Reads[MetaArtefact] =
      ( (__ \ "name"     ).read[String]
      ~ (__ \ "gitUrl"   ).readNullable[String]
      ~ (__ \ "buildInfo").read[Map[String, String]]
      )(MetaArtefact.apply)
