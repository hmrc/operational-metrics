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

import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.operationalmetrics.connector.ReleasesConnector.DeploymentEvent
import uk.gov.hmrc.operationalmetrics.connector.ReleasesConnector.WhatsRunningWhere.WhatsRunningWhere
import uk.gov.hmrc.operationalmetrics.model.{Environment, ServiceName, Version}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReleasesConnector @Inject() (
  httpClientV2  : HttpClientV2
, servicesConfig: ServicesConfig
)(using 
  ec: ExecutionContext
):
  import HttpReads.Implicits._

  private val url: String =
    servicesConfig.baseUrl("releases-api")
  
  def releases()(using HeaderCarrier): Future[Seq[WhatsRunningWhere]] =
    httpClientV2
      .get(url"$url/releases-api/whats-running-where")
      .execute[Seq[WhatsRunningWhere]]
  
  def firstCompletedDeployment(
    serviceName: ServiceName
  , version    : Version
  , environment: Environment
  )(using HeaderCarrier): Future[DeploymentEvent] =
   httpClientV2
     .get(url"$url/releases-api/firstDeployment?service=${serviceName.asString}&version=${version.toString}&environment=${environment.asString}")
     .execute[DeploymentEvent]


object ReleasesConnector:
  case class DeploymentEvent(
    serviceName: ServiceName
  , version    : Version
  , time       : Instant
  )
  
  object DeploymentEvent:
    given deploymentEventReads: Reads[DeploymentEvent] =
      ( (__ \ "serviceName").read[ServiceName]
      ~ (__ \ "version"    ).read[Version]
      ~ (__ \ "time"       ).read[Instant]
      )((service, version, time) => DeploymentEvent(service, version, time))

    def format[A, B](f: A => B, g: B => A)(using fa: Format[A]): Format[B] =
      fa.inmap(f, g)

  case class WhatsRunningWhereVersion(
    environment : Environment
  , version     : Version
  )
  
  object WhatsRunningWhereVersion:
    given whatsRunningWhereVersionReads: Reads[WhatsRunningWhereVersion] =
      ( (__ \ "environment"  ).read[Environment]
      ~ (__ \ "versionNumber").read[Version]
      )((env, version) => WhatsRunningWhereVersion(env, version))
  
  object WhatsRunningWhere:
    case class WhatsRunningWhere(
      serviceName: ServiceName
    , versions   : List[WhatsRunningWhereVersion]
    )
    
    given whatsRunningWhereReads: Reads[WhatsRunningWhere] =
      ( (__ \ "applicationName").read[ServiceName]
      ~ (__ \ "versions"       ).read[List[WhatsRunningWhereVersion]]
      )(WhatsRunningWhere.apply)
