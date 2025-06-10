package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogResponseEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceSummaryRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ServiceConfigurationService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

class BacklogRequestProcessorIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var backlogRequestRepository: BacklogRequestRepository

  @Autowired
  private lateinit var serviceConfigurationService: ServiceConfigurationService

  @Autowired
  private lateinit var serviceSummaryRepository: ServiceSummaryRepository

  private val sarCaseRef = "sar-001"
  private val testNomisId = "nomis-001"
  private val testNdeliusId = "nomis-001"
  private val dateTo = LocalDate.now()
  private val dateFrom = dateTo.minusYears(5)

  private val serviceConfigurations = listOf(
    ServiceConfiguration(
      serviceName = "service-001",
      label = "Service 1",
      url = "http://localhost:${htmlRendererApi.port()}",
      order = 1,
      enabled = true,
    ),
    ServiceConfiguration(
      serviceName = "service-002",
      label = "Service 2",
      url = "http://localhost:${htmlRendererApi.port()}",
      order = 2,
      enabled = true,
    ),
    ServiceConfiguration(
      serviceName = "service-003",
      label = "Service 3",
      url = "http://localhost:${htmlRendererApi.port()}",
      order = 3,
      enabled = true,
    ),
  )

  @BeforeEach
  fun setup() {
    backlogRequestRepository.deleteAll()
    serviceConfigurationService.deleteAll()
    serviceConfigurationService.saveAll(serviceConfigurations)
  }

  @Test
  fun `backlog request is successfully processed and marked complete`() {
    val start = LocalDateTime.now()
    val backlogRequest = createBacklogRequest()
    assertThat(backlogRequest).isNotNull

    hmppsAuth.stubGrantToken()
    serviceConfigurations.forEach { stubRendererSubjectDataHeldResponse(it.serviceName, true) }

    await()
      .atMost(3, TimeUnit.SECONDS)
      .until { requestIsComplete(backlogRequest!!.id) }

    val result = assertBacklogRequestIsComplete(
      backlogRequestId = backlogRequest!!.id,
      createdAfter = start,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
    )

    assertBacklogRequestContainsExpectedServiceSummaries(result)
  }

  private fun createBacklogRequest(): BacklogResponseEntity? = webTestClient
    .post()
    .uri("/subject-access-request/backlog")
    .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
    .bodyValue(
      CreateBacklogRequest(
        sarCaseReferenceId = sarCaseRef,
        nomisId = testNomisId,
        ndeliusCaseReferenceId = null,
        dateFrom = dateFrom,
        dateTo = dateTo,
      ),
    ).exchange()
    .expectStatus()
    .isCreated
    .returnResult(BacklogResponseEntity::class.java)
    .responseBody
    .blockFirst()

  private fun requestIsComplete(id: UUID): Boolean = backlogRequestRepository.findById(id)
    .getOrNull()
    ?.let { COMPLETE == it.status } ?: false

  private fun DynamicServicesClient.SubjectDataHeldResponse.toJson(): String = objectMapper.writeValueAsString(this)

  private fun stubRendererSubjectDataHeldResponse(serviceName: String, dataHeld: Boolean) {
    htmlRendererApi.stubSubjectDataHeldResponse(
      subjectDataHeldRequest = DynamicServicesClient.SubjectDataHeldRequest(
        nomisId = testNomisId,
        ndeliusId = null,
        serviceName = serviceName,
        serviceUrl = "http://localhost:${htmlRendererApi.port()}",
        dateFrom = dateFrom,
        dateTo = dateTo,
      ),
      responseDefinition = ResponseDefinitionBuilder
        .responseDefinition()
        .withHeader("Content-Type", "application/json")
        .withBody(
          DynamicServicesClient.SubjectDataHeldResponse(
            nomisId = testNomisId,
            ndeliusId = testNdeliusId,
            dataHeld = dataHeld,
            serviceName = serviceName,
          ).toJson(),
        ).withStatus(200),
    )
  }

  private fun assertBacklogRequestIsComplete(
    backlogRequestId: UUID,
    createdAfter: LocalDateTime,
    expectedDataHeld: Boolean,
    expectedStatus: BacklogRequestStatus,
  ): BacklogRequest {
    val backlogRequest = backlogRequestRepository.findByIdOrNull(backlogRequestId)
    assertThat(backlogRequest).isNotNull
    assertThat(backlogRequest!!.status).isEqualTo(expectedStatus)
    assertThat(backlogRequest.completedAt).isNotNull()
    assertThat(backlogRequest.completedAt).isBetween(createdAfter, LocalDateTime.now())
    assertThat(backlogRequest.dataHeld).isEqualTo(expectedDataHeld)
    return backlogRequest
  }

  private fun assertBacklogRequestContainsExpectedServiceSummaries(backlogRequest: BacklogRequest) {
    val serviceConfigurations = serviceConfigurationService.getAllServiceConfigurations()
    assertThat(backlogRequest.serviceSummary).hasSize(serviceConfigurations.size)

    serviceConfigurations.forEach { cfg ->
      val serviceSummary = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceNameAndStatus(
        backlogRequestId = backlogRequest.id,
        serviceName = cfg.serviceName,
        status = COMPLETE,
      )
      assertThat(serviceSummary).isNotNull

      assertThat(serviceSummary!!.serviceName).isEqualTo(cfg.serviceName)
      assertThat(serviceSummary.serviceOrder).isEqualTo(cfg.order)
      assertThat(serviceSummary.backlogRequest).isNotNull
      assertThat(serviceSummary.backlogRequest!!.id).isEqualTo(backlogRequest.id)
      assertThat(serviceSummary.dataHeld).isTrue()
      assertThat(serviceSummary.status).isEqualTo(COMPLETE)
    }
  }
}
