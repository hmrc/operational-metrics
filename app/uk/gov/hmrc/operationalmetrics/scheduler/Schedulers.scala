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

package uk.gov.hmrc.operationalmetrics.scheduler

import org.apache.pekko.actor.ActorSystem
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}
import play.api.inject.ApplicationLifecycle
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.lock.{MongoLockRepository, ScheduledLockService}
import uk.gov.hmrc.operationalmetrics.service.DoraMetricsService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Schedulers @Inject()(
  doraMetricsService  : DoraMetricsService
, configuration       : Configuration
, mongoLockRepository : MongoLockRepository
, timestampSupport    : TimestampSupport
)(using
  actorSystem         : ActorSystem
, applicationLifecycle: ApplicationLifecycle
, ec                  : ExecutionContext
) extends Logging:

  given HeaderCarrier = HeaderCarrier()

  private def scheduleWithLock(label: String, key: String)(f: => Future[Unit]): Unit =

    val schedulerConfig: SchedulerConfig =
      SchedulerConfig(
        configuration
      , enabledKey      = s"$key.enabled"
      , frequencyKey    = s"$key.interval"
      , initialDelayKey = s"$key.initialDelay"
      )

    val schedulerLock: ScheduledLockService =
      ScheduledLockService(
        lockRepository    = mongoLockRepository
      , lockId            = key
      , timestampSupport  = timestampSupport
      , schedulerInterval = schedulerConfig.frequency
      )

    SchedulerUtils.scheduleWithLock(label, schedulerConfig, schedulerLock)(f)

  scheduleWithLock("Service lead times updater", "service-lead-times-updater"):
    doraMetricsService
      .updateServiceLeadTimes()
      .map(_ => logger.info("Finished updating Service Lead Times"))
