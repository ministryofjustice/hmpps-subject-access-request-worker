package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogResponseEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

class BacklogRequestProcessorIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var backlogRequestRepository: BacklogRequestRepository

  private val dateTo = LocalDate.now()
  private val dateFrom = dateTo.minusYears(5)
  private val subjectDataHeldRequest = DynamicServicesClient.SubjectDataHeldRequest(
    nomisId = "",
    ndeliusId = null,
    dateFrom = dateFrom,
    dateTo = dateTo,
    serviceName = "",
    serviceUrl = "http://localhost:${htmlRendererApi.port()}",
  )

  @BeforeEach
  fun setup() {
    backlogRequestRepository.deleteAll()
  }

  @Test
  fun `backlog requests work`() {
    val resp = createBacklogRequest()
    assertThat(resp).isNotNull

    htmlRendererApi.stubSubjectDataHeldResponse()

    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestIsComplete(resp!!.id) }
  }

  fun createBacklogRequest() = webTestClient
    .post()
    .uri("/subject-access-request/backlog")
    .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
    .bodyValue(
      CreateBacklogRequest(
        sarCaseReferenceId = "sar-case-0001",
        nomisId = "nomis-0001",
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
}