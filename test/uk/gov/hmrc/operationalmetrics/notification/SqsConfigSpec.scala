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

package uk.gov.hmrc.operationalmetrics.notification

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration

class SqsConfigSpec extends AnyWordSpec with Matchers:

  "SqsConfig.endpointOverride" should:
    "be absent when not configured" in:
      sqsConfig().endpointOverride shouldBe None

    "be absent when configured as blank" in:
      sqsConfig("aws.sqs.deployment.endpointOverride" -> " ").endpointOverride shouldBe None

    "be present when configured" in:
      val config =
        sqsConfig("aws.sqs.deployment.endpointOverride" -> "https://localhost.localstack.cloud:4566")

      config.endpointOverride shouldBe Some("https://localhost.localstack.cloud:4566")

  private def sqsConfig(extraConfig: (String, Any)*): SqsConfig =
    SqsConfig(
      keyPrefix     = "aws.sqs.deployment"
    , configuration = Configuration.from(
                        Map(
                          "aws.sqs.deployment.queueUrl"            -> "https://sqs.eu-west-2.amazonaws.com/123456789012/mdtp-deployment-events"
                        , "aws.sqs.deployment.maxNumberOfMessages" -> 1
                        , "aws.sqs.deployment.waitTimeSeconds"     -> 20
                        ) ++ extraConfig.toMap
                      )
    )
