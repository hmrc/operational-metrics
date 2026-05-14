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

package uk.gov.hmrc.operationalmetrics.model

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, OFormat, __}
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType
import uk.gov.hmrc.operationalmetrics.notification.RawDeploymentEventParser.ecsEventTypeFormat

import java.time.Instant

case class DeploymentConfigFile(
  repoName: RepoName
, fileName: FileName
, commitId: CommitId
)

object DeploymentConfigFile:
  given mongoFormat: Format[DeploymentConfigFile] =
    ( (__ \ "repoName").format[RepoName]
    ~ (__ \ "fileName").format[FileName]
    ~ (__ \ "commitId").format[CommitId]
    )(DeploymentConfigFile.apply, pt => Tuple.fromProductTyped(pt))

case class DeploymentEvent(
  serviceName : ServiceName
, environment : Environment
, deploymentId: String
, eventType   : ECSEventType
, version     : Version
, time        : Instant
, userName    : UserName
, config      : Seq[DeploymentConfigFile]
, slugUri     : String
, messageId   : String
)

object DeploymentEvent:
  given mongoFormat: OFormat[DeploymentEvent] =
    given Format[ECSEventType]         = ecsEventTypeFormat
    given Format[DeploymentConfigFile] = DeploymentConfigFile.mongoFormat
    ( (__ \ "microservice"        ).format[ServiceName]
    ~ (__ \ "environment"         ).format[Environment]
    ~ (__ \ "deployment_id"       ).format[String]
    ~ (__ \ "event_type"          ).format[ECSEventType]
    ~ (__ \ "microservice_version").format[Version]
    ~ (__ \ "event_date_time"     ).format[Instant]
    ~ (__ \ "deployer_principal"  ).format[UserName]
    ~ (__ \ "config"              ).format[Seq[DeploymentConfigFile]]
    ~ (__ \ "slug_uri"            ).format[String]
    ~ (__ \ "messageId"           ).format[String]
    )(DeploymentEvent.apply, pt => Tuple.fromProductTyped(pt))
