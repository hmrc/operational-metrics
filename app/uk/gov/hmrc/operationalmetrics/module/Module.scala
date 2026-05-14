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

package uk.gov.hmrc.operationalmetrics.module

import play.api.inject.{Binding, Module as AppModule}
import play.api.{Configuration, Environment, Logging}
import uk.gov.hmrc.operationalmetrics.notification.DeploymentEventHandler
import uk.gov.hmrc.operationalmetrics.servicenow.ServiceNowEventStreamRunner
import uk.gov.hmrc.operationalmetrics.scheduler.Schedulers

import java.time.Clock

class Module extends AppModule with Logging:
  
  /** Bindings are enabled via the `sqs.enabled` config setting */
  private def ecsDeploymentsBindings(configuration: Configuration): Seq[Binding[_]] =
    if configuration.get[Boolean]("aws.sqs.enabled") then
      bind[DeploymentEventHandler].toSelf.eagerly() ::
      Nil
    else
      logger.warn("DeploymentHandler is disabled")
      Seq.empty

  override def bindings(
   environment  : Environment, 
   configuration: Configuration
  ): Seq[Binding[_]] =
    Seq(
      bind[Schedulers].toSelf.eagerly()
    , bind[Clock     ].toInstance(Clock.systemDefaultZone)
    , bind[ServiceNowEventStreamRunner].toSelf.eagerly()
    ) ++ ecsDeploymentsBindings(configuration)
