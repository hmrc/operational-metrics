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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.{Done, stream}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.operationalmetrics.connector.ArtefactProcessorConnector.MetaArtefact
import uk.gov.hmrc.operationalmetrics.connector.ReleasesConnector.HistoricDeployment
import uk.gov.hmrc.operationalmetrics.connector.{ArtefactProcessorConnector, ReleasesConnector}
import uk.gov.hmrc.operationalmetrics.model.ecs.ECSEventType
import uk.gov.hmrc.operationalmetrics.model.{CommitId, DeploymentConfigFile, DeploymentEvent, Environment, FileName, RepoName, ServiceName, UserName, Version}
import uk.gov.hmrc.operationalmetrics.persistence.{DeploymentEventsQueueRepository, ServiceNowMappingsRepository}
import uk.gov.hmrc.operationalmetrics.persistence.ServiceNowMappingsRepository.{ServiceNowMapping, defaultCmdbCI}
import uk.gov.hmrc.operationalmetrics.servicenow.model.ServiceNowEvent

import scala.concurrent.{ExecutionContextExecutor, Future}

class ServiceNowEventStreamRunnerSpec
  extends AnyWordSpec
  with MockitoSugar
  with Matchers
  with ScalaFutures
  with IntegrationPatience:

  implicit val system      : ActorSystem              = ActorSystem("TestSystem")
  implicit val ec          : ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer             = Materializer(system)

  "ServiceNowEventStreamRunner" should:
    "process and forward work items for multiple ticks" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))
      when(mockRepo.markAs(any[ObjectId], any[ProcessingStatus], any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(Some(metaArtefact)))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.successful(()))

      onTest.run(Source.repeat(()).take(2)).futureValue shouldBe Done

      verify(mockRepo       , times(5)).pullOutstanding
      verify(mockRepo       , times(3)).completeAndDelete(workItem.id)
      verify(mockSNConnector, times(3)).sendToServiceNow(any[ServiceNowEvent])

    "process and forward work items in a single tick" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))
      when(mockRepo.markAs(any[ObjectId], any[ProcessingStatus], any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(Some(metaArtefact)))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.successful(()))

      onTest.run(Source.single(())).futureValue shouldBe Done

      verify(mockRepo       , times(4)).pullOutstanding
      verify(mockRepo       , times(3)).completeAndDelete(workItem.id)
      verify(mockSNConnector, times(3)).sendToServiceNow(any[ServiceNowEvent])

    "process and forward work item when meta artefact is missing" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(None))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.successful(()))
      when(mockRepo.markAs(any[ObjectId], eqTo(ProcessingStatus.Succeeded), any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))
      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))

      onTest.run(Source.single(())).futureValue shouldBe Done

      verify(mockRepo       , times(2)).pullOutstanding
      verify(mockRepo       , times(1)).completeAndDelete(workItem.id)
      verify(mockSNConnector, times(1)).sendToServiceNow(any[ServiceNowEvent])

    "poll empty work item repository" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(None))
        .thenReturn(Future.successful(None))

      onTest.run(Source.repeat(()).take(2)).futureValue shouldBe Done

      verify(mockRepo       , times(2)).pullOutstanding
      verify(mockSNConnector, never() ).sendToServiceNow(any[ServiceNowEvent])

    "mark work item as failed after a retry when sending to service now fails" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem.copy(failureCount = 1))))
        .thenReturn(Future.successful(None))
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(Some(metaArtefact)))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.failed(RuntimeException("Service now request failed")))
      when(mockRepo.markAs(any[ObjectId], eqTo(ProcessingStatus.Failed), any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))

      onTest.run(Source.single(())).futureValue shouldBe Done

      verify(mockRepo, times(1)).markAs(workItem.id, ProcessingStatus.Failed)
      verify(mockRepo, never() ).completeAndDelete(any[ObjectId])

    "mark item as permanently failed after max retries" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem.copy(failureCount = 3))))
        .thenReturn(Future.successful(None))
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(Some(metaArtefact)))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.failed(RuntimeException("Service now request failed")))
      when(mockRepo.markAs(any[ObjectId], eqTo(ProcessingStatus.PermanentlyFailed), any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))

      onTest.run(Source.single(())).futureValue shouldBe Done

      verify(mockRepo, times(1)).markAs(workItem.id, ProcessingStatus.PermanentlyFailed)
      verify(mockRepo, never() ).completeAndDelete(any[ObjectId])

    "handle repository failures by restarting the stream" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.failed(RuntimeException("Database error"))) // end tick and restart stream
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))
      when(mockRepo.markAs(any[ObjectId], eqTo(ProcessingStatus.Succeeded), any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(Some(metaArtefact)))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.successful(()))

      onTest.run(Source.repeat(()).take(2)).futureValue shouldBe Done

      verify(mockRepo       , times(3)).pullOutstanding
      verify(mockRepo       , times(1)).completeAndDelete(workItem.id)
      verify(mockSNConnector, times(1)).sendToServiceNow(any[ServiceNowEvent])

    "forward a work item by processing it into a ServiceNow event" in new Setup:
      when(mockRepo.pullOutstanding)
        .thenReturn(Future.successful(Some(workItem)))
        .thenReturn(Future.successful(None))
      when(mockRepo.completeAndDelete(any[ObjectId]))
        .thenReturn(Future.successful(true))
      when(mockRepo.markAs(any[ObjectId], any[ProcessingStatus], any[Option[java.time.Instant]]()))
        .thenReturn(Future.successful(()))
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(Some(metaArtefact)))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.successful(()))

      onTest.run(Source.single(())).futureValue shouldBe Done

      verify(mockRepo       , times(2)).pullOutstanding
      verify(mockRepo       , times(1)).completeAndDelete(workItem.id)
      verify(mockSNConnector, times(1)).sendToServiceNow(eqTo(serviceNowEvent))

  "deploymentDescription" should:
    "return undeployment complete description" in new Setup:
      val event = deploymentEvent(ECSEventType.UnDeploymentComplete)
      onTest.deploymentDescription(event, None) shouldBe "service-1 2.0.0 undeployed in Production"

    "return undeployment failed description" in new Setup:
      val event = deploymentEvent(ECSEventType.UnDeploymentFailed)
      onTest.deploymentDescription(event, None) shouldBe "service-1 2.0.0 failed to undeploy in Production"

    "return deployment complete description with no previous version" in new Setup:
      val event = deploymentEvent(ECSEventType.DeploymentComplete)
      onTest.deploymentDescription(event, None) shouldBe "service-1 2.0.0 deployed in Production"

    "return deployment complete description with a different previous version" in new Setup:
      val event    = deploymentEvent(ECSEventType.DeploymentComplete)
      val previous = Version(1, 0, 0, "1.0.0")
      onTest.deploymentDescription(event, Some(previous)) shouldBe "service-1 2.0.0 deployed in Production (previously deployed version 1.0.0)"

    "return re-deployed description when previous version is the same" in new Setup:
      val event    = deploymentEvent(ECSEventType.DeploymentComplete)
      val previous = event.version
      onTest.deploymentDescription(event, Some(previous)) shouldBe "service-1 2.0.0 re-deployed in Production"

    "throw for unexpected event type" in new Setup:
      val event = deploymentEvent(ECSEventType.UnknownDeploymentType("unknown"))
      assertThrows[RuntimeException]:
        onTest.deploymentDescription(event, None)

  "process" should:
    "turn a work item into a ServiceNow event and send it" in new Setup:
       when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
         .thenReturn(Future.successful(Some(historicDeployment)))
       when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
         .thenReturn(Future.successful(Some(metaArtefact)))
       when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
         .thenReturn(Future.successful(()))

       onTest.process(workItem.item).futureValue

       verify(mockSNConnector, times(1)).sendToServiceNow(eqTo(serviceNowEvent))

    "derive branch as hotfix via version when no meta artefact found" in new Setup:
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(None))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.successful(()))

      onTest.process(workItem.item.copy(version = Version(1, 0, 1, "1.0.1"))).futureValue

      val event            = workItem.item.copy(version = Version(1, 0, 1, "1.0.1"))
      val shortDescription = "service-1 1.0.1 deployed in Production (previously deployed version 1.0.0)"
      val commitIds        = Seq(CommitId("config-commit-id-1"), CommitId("config-commit-id-2"))
      verify(mockSNConnector, times(1)).sendToServiceNow(eqTo(
        serviceNowEventFor(event, shortDescription, "hotfix", commitIds)
      ))

    "derive branch as main via version when no meta artefact found" in new Setup:
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(None))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.successful(()))

      onTest.process(workItem.item).futureValue

      val commitIds = Seq(CommitId("config-commit-id-1"), CommitId("config-commit-id-2"))
      verify(mockSNConnector, times(1)).sendToServiceNow(eqTo(
        serviceNowEventFor(branch = "main", commitIds = commitIds)
      ))

    "send default cmdb ci when no repository.yaml mapping exists" in new Setup:
      when(mockRConnector.previousDeployment(any[ServiceName], any[Environment], any[java.time.Instant])(using any))
        .thenReturn(Future.successful(Some(historicDeployment)))
      when(mockAPConnector.getMetaArtefact(any[String], any[Version])(using any))
        .thenReturn(Future.successful(Some(metaArtefact)))
      when(mockServiceNowMappingsRepository.find(any[String]))
        .thenReturn(Future.successful(None))
      when(mockSNConnector.sendToServiceNow(any[ServiceNowEvent]))
        .thenReturn(Future.successful(()))

      onTest.process(workItem.item).futureValue

      verify(mockSNConnector, times(1)).sendToServiceNow(eqTo(
        serviceNowEvent.copy(cmdbCI = defaultCmdbCI)
      ))

    "build a single string description containing the former payload fields" in new Setup:
      val description =
        onTest.serviceNowDescription(
          workItem.item
        , serviceNowEvent.shortDescription
        , repository
        , "main"
        , Seq(CommitId("repo-commit-id"), CommitId("config-commit-id-1"), CommitId("config-commit-id-2"))
        )

      description should include("Pipeline execution ID: 123")
      description should include("Repository: https://github.com/hmrc/service-1")
      description should include("Branch: main")
      description should include("Commit IDs: repo-commit-id, config-commit-id-1, config-commit-id-2")
      description should include("Deployment status: deployment-complete")

  trait Setup:
    given HeaderCarrier = HeaderCarrier()

    val mockConfig: Configuration =
      Configuration(
        "servicenow-stream.enabled"                  -> "false"
      , "servicenow-stream.source-tick.initialDelay" -> "1.second"
      , "servicenow-stream.source-tick.interval"     -> "1.second"
      , "queue.retryInterval"                        -> "1.second"
      )

    val mockRepo                         : DeploymentEventsQueueRepository = mock[DeploymentEventsQueueRepository]
    val mockServiceNowMappingsRepository : ServiceNowMappingsRepository    = mock[ServiceNowMappingsRepository]
    val mockSNConnector                  : ServiceNowConnector             = mock[ServiceNowConnector]
    val mockAPConnector                  : ArtefactProcessorConnector      = mock[ArtefactProcessorConnector]
    val mockRConnector                   : ReleasesConnector               = mock[ReleasesConnector]
    val serviceNowMapping                : ServiceNowMapping               = ServiceNowMapping("service-1", "service-now-mapping-1")

    when(mockServiceNowMappingsRepository.find(any[String]))
      .thenReturn(Future.successful(Some(serviceNowMapping)))

    val onTest: ServiceNowEventStreamRunner =
      ServiceNowEventStreamRunner(
        mockRepo
      , mockServiceNowMappingsRepository
      , mockConfig
      , mockRConnector
      , mockAPConnector
      , mockSNConnector
      )

    val deploymentConfigFile1 =
      DeploymentConfigFile(
        repoName = RepoName("app-config-base")
      , fileName = FileName("service-1")
      , commitId = CommitId("config-commit-id-1")
      )

    val deploymentConfigFile2 =
      DeploymentConfigFile(
        repoName = RepoName("app-config-production")
      , fileName = FileName("service-1")
      , commitId = CommitId("config-commit-id-2")
      )

    val deploymentEvent: DeploymentEvent =
      DeploymentEvent(
        serviceName  = ServiceName("service-1")
      , environment  = Environment.Production
      , deploymentId = "123"
      , eventType    = ECSEventType.DeploymentComplete
      , version      = Version(2, 0, 0, "2.0.0")
      , time         = java.time.Instant.now()
      , userName     = UserName("user-1")
      , slugUri      = "uri"
      , config       = Seq(deploymentConfigFile1, deploymentConfigFile2)
      , messageId    = "message1"
      )

    val workItem: WorkItem[DeploymentEvent] =
      WorkItem(
        id           = new ObjectId()
      , receivedAt   = java.time.Instant.now()
      , updatedAt    = java.time.Instant.now()
      , availableAt  = java.time.Instant.now()
      , status       = ProcessingStatus.ToDo
      , failureCount = 0
      , item         = deploymentEvent
      )

    val historicDeployment: HistoricDeployment =
      HistoricDeployment(
        serviceName = ServiceName("service-1")
      , version     = Version(1, 0, 0, "1.0.0")
      , time        = java.time.Instant.now()
      )

    val metaArtefact: MetaArtefact =
      MetaArtefact(
        name      = "service-1"
      , gitUrl    = Some("service-1.github.com")
      , buildInfo = Map("GIT_BRANCH" -> "main", "GIT_COMMIT" -> "repo-commit-id")
      )

    def deploymentEvent(eventType: ECSEventType): DeploymentEvent =
      workItem.item.copy(eventType = eventType)

    val repository = s"https://github.com/hmrc/service-1"

    def serviceNowEventFor(
      event           : DeploymentEvent = workItem.item
    , shortDescription: String          = s"service-1 ${workItem.item.version.original} deployed in Production (previously deployed version 1.0.0)"
    , branch          : String          = "main"
    , commitIds       : Seq[CommitId]   = Seq(CommitId("repo-commit-id"), CommitId("config-commit-id-1"), CommitId("config-commit-id-2"))
    , cmdbCI          : String          = serviceNowMapping.cmdbCI
    ): ServiceNowEvent =
      ServiceNowEvent(
        requestedBy          = event.userName
      , shortDescription     = shortDescription
      , description          = onTest.serviceNowDescription(event, shortDescription, repository, branch, commitIds)
      , cmdbCI               = cmdbCI
      )

    val serviceNowEvent: ServiceNowEvent =
      serviceNowEventFor()
