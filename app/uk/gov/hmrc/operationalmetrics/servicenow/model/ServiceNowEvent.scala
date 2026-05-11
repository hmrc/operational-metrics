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

package uk.gov.hmrc.operationalmetrics.servicenow.model

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{JsString, Writes, __}
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType
import uk.gov.hmrc.operationalmetrics.model.{CommitId, ServiceName, UserName}

import java.time.Instant

case class ServiceNowEvent(
  requestedBy         : UserName
, assignmentGroup     : String = "DevOps"
, shortDescription    : String
, pipelineExecutionId : String
, repository          : String
, branch              : String
, commitIds           : Seq[CommitId]
, artefact            : String
, testResults         : String = "Pass"
, startDateTime       : Option[Instant] = None
, endDateTime         : Instant
, deploymentStatus    : ECSEventType
, implementationResult: ECSEventType
, service             : ServiceName 
, configurationItem   : ServiceName
)

object ServiceNowEvent:
  given writes: Writes[ServiceNowEvent] =
    given Writes[ECSEventType] = Writes { event => JsString(event.value) }
    ( (__ \ "requestedBy"         ).write[UserName]
    ~ (__ \ "assignmentGroup"     ).write[String]
    ~ (__ \ "shortDescription"    ).write[String]
    ~ (__ \ "pipelineExecutionId" ).write[String]
    ~ (__ \ "repository"          ).write[String]
    ~ (__ \ "branch"              ).write[String]
    ~ (__ \ "commitIds"           ).write[Seq[CommitId]]
    ~ (__ \ "artefact"            ).write[String]
    ~ (__ \ "testResults"         ).write[String]
    ~ (__ \ "startDateTime"       ).writeNullable[Instant]
    ~ (__ \ "endDateTime"         ).write[Instant]
    ~ (__ \ "deploymentStatus"    ).write[ECSEventType]
    ~ (__ \ "implementationResult").write[ECSEventType]
    ~ (__ \ "service"             ).write[ServiceName]
    ~ (__ \ "configurationItem"   ).write[ServiceName]
    )(sne => Tuple.fromProductTyped(sne))
