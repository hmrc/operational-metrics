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

package uk.gov.hmrc.operationalmetrics.model.ecs

enum ECSEventType(val value: String):
  case DeploymentComplete     extends ECSEventType("deployment-complete"     )
  case DeploymentInProgress   extends ECSEventType("deployment-in-progress"  )
  case DeploymentFailed       extends ECSEventType("deployment-failed"       )
  case UnDeploymentComplete   extends ECSEventType("undeployment-complete"   )
  case UnDeploymentInProgress extends ECSEventType("undeployment-in-progress")
  case UnDeploymentFailed     extends ECSEventType("undeployment-failed"     )
  case UnknownDeploymentType(description: String) extends ECSEventType(description)

object ECSEventType:
  val values: List[ECSEventType] =
    List(
      DeploymentComplete
    , DeploymentInProgress
    , DeploymentFailed
    , UnDeploymentComplete
    , UnDeploymentInProgress
    , UnDeploymentFailed
    )

  def apply(str: String): ECSEventType =
    values.find(_.value == str).orElse(legacyTypes(str)).getOrElse(UnknownDeploymentType(str))

  def unapply(x: ECSEventType): Option[String] = Some(x.value)

  // ECS originally used these mappings
  private def legacyTypes(str: String): Option[ECSEventType] =
    str match
      case "CREATE_COMPLETE"    | "UPDATE_COMPLETE"    => Some(DeploymentComplete)
      case "CREATE_FAILED"      | "UPDATE_FAILED"      => Some(DeploymentFailed)
      case "CREATE_IN_PROGRESS" | "UPDATE_IN_PROGRESS" => Some(DeploymentInProgress)
      case "DELETE_COMPLETE"                           => Some(UnDeploymentComplete)
      case "DELETE_FAILED"                             => Some(UnDeploymentFailed)
      case "DELETE_IN_PROGRESS"                        => Some(UnDeploymentInProgress)
      case _                                           => None
