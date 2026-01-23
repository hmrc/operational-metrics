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
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.operationalmetrics.connector.ServiceDependenciesConnector.SlugInfo
import uk.gov.hmrc.operationalmetrics.model.{ServiceName, Version}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ServiceDependenciesConnector @Inject() (
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(using
  ec: ExecutionContext
):
  import HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("service-dependencies")
  
  def getSlugCreationDate(
    service: ServiceName
  , version: Version
  )(using HeaderCarrier): Future[SlugInfo] =
    httpClientV2
      .get(url"$url/api/sluginfo?name=${service.asString}&version=${version.toString}")
      .execute[SlugInfo]

object ServiceDependenciesConnector:
  case class SlugInfo(
    name   : ServiceName
  , version: Version
  , created: Instant
  )

  object SlugInfo:
    given reads: Reads[SlugInfo] =
      ( (__ \ "name"   ).read[ServiceName]
      ~ (__ \ "version").read[Version]
      ~ (__ \ "created").read[Instant]
      )((name, version, created) => SlugInfo(name, version, created))
