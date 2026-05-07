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

import play.api.libs.json.*
import play.api.libs.functional.syntax.*

case class ServiceName(asString: String) extends AnyVal
object ServiceName:
  given Format[ServiceName] =
    Format.of[String].inmap(ServiceName.apply, _.asString)

case class RepoName(asString: String) extends AnyVal
object RepoName:
  given Format[RepoName] =
    Format.of[String].inmap(RepoName.apply, _.asString)

case class FileName(asString: String) extends AnyVal
object FileName:
  given Format[FileName] =
    Format.of[String].inmap(FileName.apply, _.asString)

case class CommitId(asString: String) extends AnyVal
object CommitId:
  given Format[CommitId] =
    Format.of[String].inmap(CommitId.apply, _.asString)

case class UserName(asString: String) extends AnyVal
object UserName:
  given Format[UserName] =
    Format.of[String].inmap(UserName.apply, _.asString)

case class Version(
  major   : Int
, minor   : Int
, patch   : Int
, original: String
):
  override def toString: String = original
  
  def isHotfix: Boolean =
    patch != 0

object Version:
  given Format[Version] =
    new Format[Version]:
      override def reads(json: JsValue): JsResult[Version] =
        json match
          case JsString(s) => Version.parse(s).map(v => JsSuccess(v)).getOrElse(JsError("Could not parse version"))
          case _           => JsError("Not a string")

      override def writes(v: Version): JsString =
        JsString(v.original)

  private val regex3 = """(\d+)\.(\d+)\.(\d+)(.*)""".r
  private val regex2 = """(\d+)\.(\d+)(.*)""".r
  private val regex1 = """(\d+)(.*)""".r

  def parse(s: String): Option[Version] =
    s match
      case regex3(maj, min, patch, _) => Some(Version(Integer.parseInt(maj), Integer.parseInt(min), Integer.parseInt(patch), s))
      case regex2(maj, min,  _)       => Some(Version(Integer.parseInt(maj), Integer.parseInt(min), 0                      , s))
      case regex1(patch,  _)          => Some(Version(0                    , 0                    , Integer.parseInt(patch), s))
      case _                          => None

  def apply(version: String): Version =
    version match
      case regex3(maj, min, patch, _) => Version(Integer.parseInt(maj), Integer.parseInt(min), Integer.parseInt(patch), version)
      case regex2(maj, min,  _)       => Version(Integer.parseInt(maj), Integer.parseInt(min), 0                      , version)
      case regex1(patch,  _)          => Version(0                    , 0                    , Integer.parseInt(patch), version)
      case _                          => Version(0                    , 0                    , 0                      , version)
