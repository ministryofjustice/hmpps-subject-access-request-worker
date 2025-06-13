package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
      serviceName = "service-1",
      label = "Service 1",
      url = "http://localhost:${htmlRendererApi.port()}",
      order = 1,
      enabled = true,
    ),
    ServiceConfiguration(
      serviceName = "service-2",
      label = "Service 2",
      url = "http://localhost:${htmlRendererApi.port()}",
      order = 2,
      enabled = true,
    ),
    ServiceConfiguration(
      serviceName = "service-3",
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

  @ParameterizedTest
  @CsvSource(
    value = [
      "true   | 'Completed backlog request should show data held TRUE when services holds data on subject'",
      "false  | 'Completed backlog request should show data held FALSE when services does not hold data on subject'",
    ],
    delimiter = '|',
  )
  fun `backlog request is successfully processed when subject data is held`(
    dataIsHeld: Boolean,
    description: String,
  ) {
    val start = LocalDateTime.now()
    val backlogRequest = createBacklogRequest()
    assertThat(backlogRequest).isNotNull

    hmppsAuth.stubGrantToken()
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-1"), dataIsHeld)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-2"), dataIsHeld)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-3"), dataIsHeld)

    await()
      .atMost(5, TimeUnit.SECONDS)
      .until { requestIsComplete(backlogRequest!!.id) }

    val result = assertBacklogRequestEqualsExpected(
      backlogRequestId = backlogRequest!!.id,
      createdAfter = start,
      expectedDataHeld = dataIsHeld,
      expectedStatus = COMPLETE,
      expectedClaimDateTimeAfter = start,
    )

    assertThat(result.serviceSummary).hasSize(serviceConfigurations.size)
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-1",
      expectedOrder = 1,
      expectedDataHeld = dataIsHeld,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-2",
      expectedOrder = 2,
      expectedDataHeld = dataIsHeld,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-3",
      expectedOrder = 3,
      expectedDataHeld = dataIsHeld,
      expectedStatus = COMPLETE,
    )

    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-1"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-2"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-3"))
  }

  @Test
  fun `backlog request is successful after retrying failed renderer request`() {
    val start = LocalDateTime.now()
    val backlogRequest = createBacklogRequest()
    assertThat(backlogRequest).isNotNull

    hmppsAuth.stubGrantToken()
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-1"), true)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-2"), true)
    stubRendererSubjectDataHeldFailsOnFirstAttempt(createSubjectDataHeldRequest("service-3"), true)

    await()
      .atMost(3, TimeUnit.SECONDS)
      .until { requestIsComplete(backlogRequest!!.id) }

    val result = assertBacklogRequestEqualsExpected(
      backlogRequestId = backlogRequest!!.id,
      createdAfter = start,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
      expectedClaimDateTimeAfter = start,
    )

    assertThat(result.serviceSummary).hasSize(serviceConfigurations.size)
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-1",
      expectedOrder = 1,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-2",
      expectedOrder = 2,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-3",
      expectedOrder = 3,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
    )

    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-1"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-2"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(2, createSubjectDataHeldRequest("service-3"))
  }

  @Test
  fun `backlog request is successful with data held TRUE when some services do not hold subject data`() {
    val start = LocalDateTime.now()
    val backlogRequest = createBacklogRequest()
    assertThat(backlogRequest).isNotNull

    hmppsAuth.stubGrantToken()
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-1"), false)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-2"), false)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-3"), true)

    await()
      .atMost(3, TimeUnit.SECONDS)
      .until { requestIsComplete(backlogRequest!!.id) }

    val result = assertBacklogRequestEqualsExpected(
      backlogRequestId = backlogRequest!!.id,
      createdAfter = start,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
      expectedClaimDateTimeAfter = start,
    )

    val serviceConfigurations = serviceConfigurationService.getAllServiceConfigurations()
    assertThat(result.serviceSummary).hasSize(serviceConfigurations.size)

    assertThat(result.serviceSummary).hasSize(serviceConfigurations.size)
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-1",
      expectedOrder = 1,
      expectedDataHeld = false,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-2",
      expectedOrder = 2,
      expectedDataHeld = false,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-3",
      expectedOrder = 3,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
    )

    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-1"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-2"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-3"))
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

  private fun stubRendererSubjectDataHeldResponse(
    expectedSubjectDataHeldRequest: DynamicServicesClient.SubjectDataHeldRequest,
    dataHeld: Boolean,
  ) {
    htmlRendererApi.stubSubjectDataHeldResponse(
      subjectDataHeldRequest = expectedSubjectDataHeldRequest,
      responseDefinition = ResponseDefinitionBuilder
        .responseDefinition()
        .withHeader("Content-Type", "application/json")
        .withBody(
          DynamicServicesClient.SubjectDataHeldResponse(
            nomisId = testNomisId,
            ndeliusId = testNdeliusId,
            dataHeld = dataHeld,
            serviceName = expectedSubjectDataHeldRequest.serviceName,
          ).toJson(),
        ).withStatus(200),
    )
  }

  private fun stubRendererSubjectDataHeldFailsOnFirstAttempt(
    expectedSubjectDataHeldRequest: DynamicServicesClient.SubjectDataHeldRequest,
    dataHeld: Boolean,
  ) {
    htmlRendererApi.stubSubjectDataHeldResponseRetryAfterFailure(
      subjectDataHeldRequest = expectedSubjectDataHeldRequest,
      responseOne = ResponseDefinitionBuilder
        .responseDefinition()
        .withHeader("Content-Type", "application/json")
        .withStatus(500),
      responseTwo = ResponseDefinitionBuilder
        .responseDefinition()
        .withHeader("Content-Type", "application/json")
        .withBody(
          DynamicServicesClient.SubjectDataHeldResponse(
            nomisId = testNomisId,
            ndeliusId = testNdeliusId,
            dataHeld = dataHeld,
            serviceName = expectedSubjectDataHeldRequest.serviceName,
          ).toJson(),
        ).withStatus(200),
    )
  }

  private fun assertBacklogRequestEqualsExpected(
    backlogRequestId: UUID,
    createdAfter: LocalDateTime,
    expectedDataHeld: Boolean,
    expectedStatus: BacklogRequestStatus,
    expectedClaimDateTimeAfter: LocalDateTime?,
  ): BacklogRequest {
    val backlogRequest = backlogRequestRepository.findByIdOrNull(backlogRequestId)
    assertThat(backlogRequest).isNotNull
    assertThat(backlogRequest!!.status).isEqualTo(expectedStatus)
    assertThat(backlogRequest.completedAt).isNotNull()
    assertThat(backlogRequest.completedAt).isBetween(createdAfter, LocalDateTime.now())
    assertThat(backlogRequest.dataHeld).isEqualTo(expectedDataHeld)
    expectedClaimDateTimeAfter?.let {
      assertThat(backlogRequest.claimDateTime).isBetween(it, LocalDateTime.now())
    }
    return backlogRequest
  }

  private fun assertServiceSummaryExistsWithExpectedValues(
    backlogRequestId: UUID,
    serviceName: String,
    expectedOrder: Int,
    expectedDataHeld: Boolean,
    expectedStatus: BacklogRequestStatus,
  ) {
    val serviceSummary = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceNameAndStatus(
      backlogRequestId = backlogRequestId,
      serviceName = serviceName,
      status = COMPLETE,
    )
    assertThat(serviceSummary).isNotNull

    assertThat(serviceSummary!!.serviceName).isEqualTo(serviceName)
    assertThat(serviceSummary.serviceOrder).isEqualTo(expectedOrder)
    assertThat(serviceSummary.backlogRequest).isNotNull
    assertThat(serviceSummary.backlogRequest!!.id).isEqualTo(backlogRequestId)
    assertThat(serviceSummary.dataHeld).isEqualTo(expectedDataHeld)
    assertThat(serviceSummary.status).isEqualTo(expectedStatus)
  }

  private fun createSubjectDataHeldRequest(serviceName: String) = DynamicServicesClient.SubjectDataHeldRequest(
    nomisId = testNomisId,
    ndeliusId = null,
    serviceName = serviceName,
    serviceUrl = "http://localhost:${htmlRendererApi.port()}",
    dateFrom = dateFrom,
    dateTo = dateTo,
  )
}
