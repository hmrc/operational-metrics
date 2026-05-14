/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.Applicative
import cats.implicits.*
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.operationalmetrics.model.*
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType

import java.time.Instant
import scala.collection.immutable.TreeMap

object RawDeploymentEventParser extends DefaultJsonFormats:

  private given Applicative[Reads] =
    new Applicative[Reads]:
      override def pure[A](a: A): Reads[A] =
        Reads.pure(a)

      override def ap[A, B](ff: Reads[A => B])(fa: Reads[A]): Reads[B] =
        for
          f <- ff
          a <- fa
        yield f(a)

  private val ConfigKey = ".*\\.(\\d+)\\.(\\w+)".r

  lazy val deploymentEventReads: Reads[DeploymentEvent] =
    summon[Reads[JsObject]]
      .flatMap: jsObject =>
        for
          config <- TreeMap(
                      jsObject.fields
                        .collect { case (ConfigKey(i, k), v) => (i.toInt, k, v.as[String]) }
                        .groupBy(_._1)
                        .toSeq: _*
                    )
                    .toList
                    .traverse:
                      case (i, s) =>
                        for
                          repoName <- s.collectFirst { case (_, k, v) if k == "repoName" => Reads.pure(RepoName(v)) }.getOrElse(Reads.failed(s"config.$i missing repoName"))
                          fileName <- s.collectFirst { case (_, k, v) if k == "fileName" => Reads.pure(FileName(v)) }.getOrElse(Reads.failed(s"config.$i missing fileName"))
                          commitId <- s.collectFirst { case (_, k, v) if k == "gitSha"   => Reads.pure(CommitId(v)) }.getOrElse(Reads.failed(s"config.$i missing gitSha"  ))
                        yield DeploymentConfigFile(repoName = repoName, fileName = fileName, commitId = commitId)
          res    <- deploymentEventReads1.map(_.copy(config = config))
        yield res

  private lazy val deploymentEventReads1: Reads[DeploymentEvent] =
    given Format[ECSEventType] = ecsEventTypeFormat
    ( (__ \ "microservice"                      ).read[ServiceName]
    ~ (__ \ "environment"                       ).read[Environment]
    ~ (__ \ "deployment_id"                     ).read[String]
    ~ (__ \ "event_type"                        ).read[ECSEventType]
    ~ (__ \ "microservice_version"              ).read[Version]
    ~ (__ \ "event_date_time"                   ).read[Instant]
    ~ (__ \ "deployer_principal"                ).readNullable[UserName].map(_.getOrElse(UserName.unknown))
    ~ Reads.pure(Seq.empty[DeploymentConfigFile]) // config - to be added
    ~ (__ \ "slug_uri"                          ).read[String]
    ~ Reads.pure("")                             // messageId - to be added
    )(DeploymentEvent.apply _)
