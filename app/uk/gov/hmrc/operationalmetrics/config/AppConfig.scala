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

package uk.gov.hmrc.operationalmetrics.config

import com.codahale.metrics.{Counter, Metric, MetricRegistry, NoopMetricRegistry, SharedMetricRegistries}

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.operationalmetrics.servicenow.ServiceNowNotificationMetrics.{ServiceNowDeployMetricKey, ServiceNowNotification}

import scala.concurrent.duration.{Duration, DurationLong, FiniteDuration}

@Singleton
class AppConfig @Inject()(val config: Configuration):

  val appName: String = config.get[String]("appName")
  val serviceNowConfig: ServiceNowConfig = ServiceNowConfig()

  case class ServiceNowConfig(
    serviceNowStreamEnabled: Boolean             = config.get[Boolean]("servicenow-stream.enabled"),

    streamSourceTickInitialDelay: FiniteDuration = config.get[Duration]("servicenow-stream.source-tick.initialDelay").toMillis.millis,
    streamSourceTickInterval: FiniteDuration     = config.get[Duration]("servicenow-stream.source-tick.interval"    ).toMillis.millis,
    defaultCmdbCI: String                        = config.get[String]("servicenow.default-cmdb-ci")
  )

  val metricsConfig: MetricsConfig = MetricsConfig()

  case class MetricsConfig(
    graphiteEnabled: Boolean = config.getOptional[Boolean]("microservice.metrics.graphite.enabled").getOrElse(false)
  ) {
    val registry = setupMetricRegistry

    val serviceNowNotificationMetrics: Map[ServiceNowNotification, Metric] = {
      Map(
        ServiceNowNotification.SuccessfulySent -> registry.counter(ServiceNowDeployMetricKey + ".successful"),
        ServiceNowNotification.Failed -> registry.counter(ServiceNowDeployMetricKey + ".failed"),
        ServiceNowNotification.EventRejected -> registry.counter(ServiceNowDeployMetricKey + ".rejected")
        )
    }

    private def setupMetricRegistry = {
      if (graphiteEnabled) then {
        SharedMetricRegistries.getOrCreate(appName)
      }
      else
        new NoopMetricRegistry
    }
  }
