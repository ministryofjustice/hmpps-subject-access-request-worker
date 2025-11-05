package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestOverview
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceSummaryRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ServiceConfigurationService
import java.time.LocalDate
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

abstract class BaseBacklogRequestIntTest : IntegrationTestBase() {

  @Autowired
  protected lateinit var backlogRequestRepository: BacklogRequestRepository

  @Autowired
  protected lateinit var serviceConfigurationService: ServiceConfigurationService

  @Autowired
  protected lateinit var serviceSummaryRepository: ServiceSummaryRepository

  protected val sarCaseRef = "sar-001"
  protected val testNomisId = "nomis-001"
  protected val testNdeliusId = "nomis-001"
  protected val dateTo = LocalDate.now()
  protected val dateFrom = dateTo.minusYears(5)

  protected val serviceConfigurations = listOf(
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
    customSetup()
  }

  abstract fun customSetup()

  protected fun requestIsComplete(id: UUID): Boolean = backlogRequestRepository.findById(id)
    .getOrNull()
    ?.let { COMPLETE == it.status } ?: false

  protected fun requestHasAtLeastNCompleteSummaries(id: UUID, n: Int) = backlogRequestRepository
    .findByIdOrNull(id)?.let { req -> req.serviceSummary.count { summary -> summary.status == COMPLETE } >= n }
    ?: false

  protected fun renderMockIsCalledNTimes(n: Int) = htmlRendererApi.allServeEvents.count() == n

  protected fun HtmlRendererApiClient.SubjectDataHeldResponse.toJson(): String = objectMapper.writeValueAsString(this)

  protected fun stubRendererSubjectDataHeldResponse(
    expectedSubjectDataHeldRequest: HtmlRendererApiClient.SubjectDataHeldRequest,
    dataHeld: Boolean,
  ) {
    htmlRendererApi.stubSubjectDataHeldResponse(
      subjectDataHeldRequest = expectedSubjectDataHeldRequest,
      responseDefinition = ResponseDefinitionBuilder
        .responseDefinition()
        .withHeader("Content-Type", "application/json")
        .withBody(
          HtmlRendererApiClient.SubjectDataHeldResponse(
            nomisId = testNomisId,
            ndeliusId = null,
            dataHeld = dataHeld,
            serviceName = expectedSubjectDataHeldRequest.serviceName,
          ).toJson(),
        ).withStatus(200)
        .withUniformRandomDelay(10, 2000),
    )
  }

  protected fun stubRendererSubjectDataHeldResponseError(
    expectedSubjectDataHeldRequest: HtmlRendererApiClient.SubjectDataHeldRequest,
    status: Int,
  ) {
    htmlRendererApi.stubSubjectDataHeldResponse(
      subjectDataHeldRequest = expectedSubjectDataHeldRequest,
      responseDefinition = ResponseDefinitionBuilder
        .responseDefinition()
        .withHeader("Content-Type", "application/json")
        .withStatus(status)
        .withUniformRandomDelay(10, 2000),
    )
  }

  protected fun createSubjectDataHeldRequest(serviceName: String) = HtmlRendererApiClient.SubjectDataHeldRequest(
    nomisId = testNomisId,
    ndeliusId = null,
    serviceName = serviceName,
    serviceUrl = "http://localhost:${htmlRendererApi.port()}",
    dateFrom = dateFrom,
    dateTo = dateTo,
  )

  protected fun createBacklogRequest(): BacklogRequestOverview? = webTestClient
    .post()
    .uri("/subject-access-request/backlog")
    .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
    .bodyValue(
      CreateBacklogRequest(
        subjectName = "Jailbird, Snake",
        version = "1",
        sarCaseReferenceNumber = sarCaseRef,
        nomisId = testNomisId,
        ndeliusCaseReferenceId = null,
        dateFrom = dateFrom,
        dateTo = dateTo,
      ),
    ).exchange()
    .expectStatus()
    .isCreated
    .returnResult(BacklogRequestOverview::class.java)
    .responseBody
    .blockFirst()

  protected fun postBacklogRequestExpectSuccess(request: CreateBacklogRequest): BacklogRequestOverview {
    val resp = webTestClient
      .post()
      .uri("/subject-access-request/backlog")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
      .bodyValue(request)
      .exchange()
      .expectStatus().isCreated
      .returnResult(BacklogRequestOverview::class.java)
    return resp.responseBody.blockFirst()!!
  }
}
