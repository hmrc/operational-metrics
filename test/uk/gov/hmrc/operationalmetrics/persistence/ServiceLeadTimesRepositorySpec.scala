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

package uk.gov.hmrc.operationalmetrics.persistence

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.operationalmetrics.model.{Environment, LeadTimeMeasurement, ServiceLeadTimes, ServiceName, Version}

import java.time.Instant

class ServiceLeadTimesRepositorySpec
  extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[ServiceLeadTimes]:

  override protected val repository: ServiceLeadTimesRepository = ServiceLeadTimesRepository(mongoComponent)

  val created : Instant = Instant.parse("2026-01-07T11:51:23.000Z")
  val deployed: Instant = Instant.parse("2026-01-09T11:51:23.000Z")

  val leadTimes1: ServiceLeadTimes =
    ServiceLeadTimes(
      serviceName = ServiceName("service-1")
    , leadTimes   = Seq(LeadTimeMeasurement(environment = Environment.Production, version = Version("1.0.0"), slugCreatedAt = created, firstDeployedAt = deployed, days = 2))
    )

  val leadTimes2: ServiceLeadTimes =
    ServiceLeadTimes(
      serviceName = ServiceName("service-2")
    , leadTimes   = Seq(LeadTimeMeasurement(environment = Environment.Production, version = Version("1.0.0"), slugCreatedAt = created, firstDeployedAt = deployed, days = 2))
    )

  val leadTimes3: ServiceLeadTimes =
    ServiceLeadTimes(
      serviceName = ServiceName("service-3")
    , leadTimes   = Seq(LeadTimeMeasurement(environment = Environment.Production, version = Version("1.0.0"), slugCreatedAt = created, firstDeployedAt = deployed, days = 2))
    )

  "ServiceLeadTimesRepository.putAll" should:
    "put correctly" in:
      repository.putAll(Seq(leadTimes1, leadTimes2)).futureValue
      findAll().futureValue should contain.only(leadTimes1, leadTimes2)

      val updatedLeadTimes2 =
        leadTimes2.copy(
          leadTimes = leadTimes2.leadTimes.map: ltm =>
            ltm.copy(version = Version("2.0.0"))
        )

      repository.putAll(Seq(updatedLeadTimes2, leadTimes3)).futureValue
      findAll().futureValue should contain.only(updatedLeadTimes2, leadTimes3)

  "ServiceLeadTimesRepository.findAll" should:
    "find all service lead times" in:
      repository.putAll(Seq(leadTimes1, leadTimes2, leadTimes3)).futureValue
      repository.findAll().futureValue shouldBe Seq(leadTimes1, leadTimes2, leadTimes3)
