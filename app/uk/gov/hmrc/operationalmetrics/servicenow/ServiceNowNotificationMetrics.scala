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

import com.codahale.metrics.SharedMetricRegistries
import ServiceNowNotificationMetrics.{ServiceNowDeployMetricKey, ServiceNowNotification}

trait ServiceNowNotificationMetrics:
  
  protected val metricRegistry = SharedMetricRegistries.getOrCreate(ServiceNowDeployMetricKey)

  def recordSuccess(): Unit =
    incrementMetricCounter(ServiceNowNotification.SuccessfulySent)
  
  def recordFail(): Unit =
    incrementMetricCounter(ServiceNowNotification.Failed)

  def recordRejected(): Unit =
    incrementMetricCounter(ServiceNowNotification.EventRejected)

  private def incrementMetricCounter(notification: ServiceNowNotification): Unit =
    metricRegistry.counter(notification.metricId).inc()

object ServiceNowNotificationMetrics:
  val ServiceNowDeployMetricKey: String = "ServiceNow-Send-Notification-Metrics"

  enum ServiceNowNotification(val metricId: String):
    case SuccessfulySent extends ServiceNowNotification(s"$ServiceNowDeployMetricKey.successful")
    case Failed extends ServiceNowNotification(s"$ServiceNowDeployMetricKey.failed")
    case EventRejected extends ServiceNowNotification(s"$ServiceNowDeployMetricKey.rejected")
