package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogResponseEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

class BacklogRequestProcessorIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var backlogRequestRepository: BacklogRequestRepository

  @Autowired
  private lateinit var serviceConfigurationRepository: ServiceConfigurationRepository

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
    serviceConfigurationRepository.deleteAll()
    serviceConfigurationRepository.saveAll(serviceConfigurations)
  }

  @Test
  fun `backlog requests work`() {
    val backlogRequest = createBacklogRequest()
    assertThat(backlogRequest).isNotNull

    println(backlogRequest)

    hmppsAuth.stubGrantToken()
    serviceConfigurations.forEach {
      println("Setting up stub....")
      stubRendererSubjectDataHeldResponse(it.serviceName, true)
    }

    await()
      .atMost(3, TimeUnit.SECONDS)
      .until { requestIsComplete(backlogRequest!!.id) }

    println(backlogRequestRepository.findById(backlogRequest!!.id))
  }

  @Test
  fun bob() {
    hmppsAuth.stubGrantToken()
    serviceConfigurations.forEach {
      stubRendererSubjectDataHeldResponse(it.serviceName, true)
    }

    val resp = webTestClient.mutate().baseUrl("http://localhost:${htmlRendererApi.port()}/")
      .build()
      .post()
      .uri("/subject-access-request/subject-data-held-summary").bodyValue(
        DynamicServicesClient.SubjectDataHeldRequest(
          nomisId = testNomisId,
          ndeliusId = null,
          serviceName = "service-001",
          serviceUrl = "http://localhost:${htmlRendererApi.port()}",
          dateFrom = dateFrom,
          dateTo = dateTo,
        ),
      ).exchange()
      .expectStatus().isOk
      .returnResult(String::class.java)

    println("RESPONSE: ${resp.responseBody.blockFirst()}")
  }

  fun createBacklogRequest() = webTestClient
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

  fun requestIsComplete(id: UUID): Boolean = backlogRequestRepository.findById(id)
    .getOrNull()
    ?.let { BacklogRequestStatus.COMPLETE == it.status } ?: false

  fun DynamicServicesClient.SubjectDataHeldResponse.toJson(): String = objectMapper.writeValueAsString(this)

  fun stubRendererSubjectDataHeldResponse(serviceName: String, dataHeld: Boolean) {
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
}
