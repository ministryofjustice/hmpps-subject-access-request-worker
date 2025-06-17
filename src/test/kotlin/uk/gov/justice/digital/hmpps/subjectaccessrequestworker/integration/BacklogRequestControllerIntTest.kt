package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class BacklogRequestControllerIntTest : IntegrationTestBase() {

  private companion object {
    const val SAR_CASE_REF_INVALID_MSG = "non null/empty value is required for sarCaseReferenceId"
    const val NOMIS_NDELIUS_ID_INVALID_MSG = "a non null/empty value is required for nomisId or ndeliusCaseReferenceId"
    const val MULTIPLE_SUBJECT_IDS_PROVIDED_MSG = "multiple ID's provided provided please provide either a nomisId or ndeliusCaseReferenceId"
    const val SUBJECT_NAME_NULL_OR_EMPTY_MSG = "a non null/empty value is required for subject name"
    const val VERSION_NULL_OR_EMPTY_MSG = "a non null/empty value is required for version"
  }

  @Autowired
  lateinit var backlogRequestService: BacklogRequestService

  private val createBacklogRequest = CreateBacklogRequest(
    subjectName = "Jailbird, Snake",
    version = "1",
    sarCaseReferenceId = "test-001",
    ndeliusCaseReferenceId = null,
    nomisId = null,
    dateFrom = LocalDate.now().minusYears(1),
    dateTo = LocalDate.now(),
  )

  @Nested
  inner class AuthenticationTestCases {
    @Test
    fun `should return status 401 if no auth header is present`() {
      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .bodyValue(createBacklogRequest)
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return status 403 if auth header does not contain the required role`() {
      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS")))
        .bodyValue(createBacklogRequest)
        .exchange()
        .expectStatus()
    }
  }

  @Nested
  inner class ValidationTestCases {

    @ParameterizedTest
    @CsvSource(
      value = [
        "    |    |    |      |    | $SAR_CASE_REF_INVALID_MSG          | sarCaseReferenceId is null",
        " '' |    |    |      |    | $SAR_CASE_REF_INVALID_MSG          | sarCaseReferenceId is empty",
        "A1  |    |    |      |    | $NOMIS_NDELIUS_ID_INVALID_MSG      | nomisId and ndeliusCaseReferenceId are null",
        "A1  | '' |    |      |    | $NOMIS_NDELIUS_ID_INVALID_MSG      | nomisId is empty ndeliusCaseReferenceId is null",
        "A1  | '' | '' |      |    | $NOMIS_NDELIUS_ID_INVALID_MSG      | nomisId and ndeliusCaseReferenceId are empty",
        "A1  |    | '' |      |    | $NOMIS_NDELIUS_ID_INVALID_MSG      | nomisId is null ndeliusCaseReferenceId is empty",
        "A1  | B2 | C2 |      |    | $MULTIPLE_SUBJECT_IDS_PROVIDED_MSG | both nomis and ndelius IDs provided",
        "A1  | B2 |    |      |    | $SUBJECT_NAME_NULL_OR_EMPTY_MSG    | subject name is null",
        "A1  |    | C2 | ''   |    | $SUBJECT_NAME_NULL_OR_EMPTY_MSG    | subject name is empty",
        "A1  |    | C2 | 'D2' |    | $VERSION_NULL_OR_EMPTY_MSG         | version is null",
        "A1  |    | C2 | 'D2' | '' | $VERSION_NULL_OR_EMPTY_MSG         | version is empty",
      ],
      delimiterString = "|",
    )
    fun `should return status 400 if mandatory fields are null or empty`(
      sarCaseReferenceId: String?,
      nomisId: String?,
      ndeliusCaseReferenceId: String?,
      subjectName: String?,
      version: String?,
      expectedMessage: String,
      description: String,
    ) {
      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .bodyValue(
          CreateBacklogRequest(
            subjectName = subjectName,
            version = version,
            sarCaseReferenceId = sarCaseReferenceId,
            ndeliusCaseReferenceId = ndeliusCaseReferenceId,
            nomisId = nomisId,
            dateFrom = LocalDate.now().minusYears(1),
            dateTo = LocalDate.now(),
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.developerMessage")
        .isEqualTo(expectedMessage)
    }
  }

  @Nested
  inner class GetBacklogRequestsTestCases {

    @Test
    fun `should return empty when no requests exist`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun `should return expected result when requests exist`() {
      val req = createBacklogRequest("001")

      webTestClient
        .get()
        .uri("/subject-access-request/backlog")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$.[0].id").isEqualTo(req.id)
    }
  }

  @Nested
  inner class GetBacklogRequestByIdTestCases {

    @Test
    fun `should return 404 when no requests exist`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return expected result when requests exist`() {
      val req = createBacklogRequest("002")

      webTestClient
        .get()
        .uri("/subject-access-request/backlog/${req.id}")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(req.id)
        .jsonPath("$.sarCaseReferenceId").isEqualTo("002")
        .jsonPath("$.nomisId").isEqualTo("002")
        .jsonPath("$.status").isEqualTo("PENDING")
        .jsonPath("$.serviceSummary").isArray
        .jsonPath("$.serviceSummary.length()").isEqualTo(0)
        .jsonPath("$.createdDate").isNotEmpty
        .jsonPath("$.subjectName").isEqualTo("Jailbird, Snake")
        .jsonPath("$.version").isEqualTo("1")
    }
  }

  @Nested
  inner class CreateBacklogRequestTestCases {

    @ParameterizedTest
    @CsvSource(
      value = [
        "nomis-001 |             | nomisID provide ndeliusId is null",
        "          | ndelius-001 | nomisID provide ndeliusId is null",
      ],
      delimiterString = "|",
    )
    fun `should return status 200 when valid request with NOMIS ID is provided`(
      nomisId: String?,
      ndeliusId: String?,
      description: String,
    ) {
      val startOfTest = LocalDateTime.now()
      val request = CreateBacklogRequest(
        subjectName = "Jailbird, Snake",
        version = "1",
        sarCaseReferenceId = "test-001",
        ndeliusCaseReferenceId = ndeliusId,
        nomisId = nomisId,
        dateFrom = LocalDate.now().minusYears(1),
        dateTo = LocalDate.now(),
      )

      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated
        .expectBody()
        .jsonPath("$.id").isNotEmpty
        .jsonPath("$.sarCaseReferenceId").isEqualTo(request.sarCaseReferenceId!!)
        .jsonPath("$.nomisId").isEqualTo(nomisId)
        .jsonPath("$.ndeliusCaseReferenceId").isEqualTo(ndeliusId)
        .jsonPath("$.status").isEqualTo("PENDING")
        .jsonPath("$.createdDate").value<String> {
          LocalDateTime.parse(it).isAfter(startOfTest)
          LocalDateTime.parse(it).isBefore(LocalDateTime.now())
        }
        .jsonPath("$.serviceSummary").isArray
        .jsonPath("$.serviceSummary.length()").isEqualTo(0)
    }
  }

  @Nested
  inner class BacklogStatusEntityTestCases {

    @Test
    fun `should return status 401 for backlog requests status when no auth header provided`() {
      webTestClient.get()
        .uri("/subject-access-request/backlog/status")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return backlog requests status success`() {
      backlogRequestService.save(
        BacklogRequest(
          sarCaseReferenceNumber = "1",
          nomisId = "1",
          ndeliusCaseReferenceId = null,
          dateFrom = LocalDate.now().minusYears(1),
          dateTo = LocalDate.now(),
          dataHeld = true,
          status = BacklogRequestStatus.COMPLETE,
        ),
      )

      backlogRequestService.save(
        BacklogRequest(
          sarCaseReferenceNumber = "2",
          nomisId = "2",
          ndeliusCaseReferenceId = null,
          dateFrom = LocalDate.now().minusYears(1),
          dateTo = LocalDate.now(),
          dataHeld = false,
        ),
      )

      backlogRequestService.save(
        BacklogRequest(
          sarCaseReferenceNumber = "3",
          nomisId = null,
          ndeliusCaseReferenceId = "3",
          dateFrom = LocalDate.now().minusYears(1),
          dateTo = LocalDate.now(),
          dataHeld = false,
        ),
      )

      webTestClient.get()
        .uri("/subject-access-request/backlog/status")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.totalRequests").isEqualTo(3)
        .jsonPath("$.pendingRequests").isEqualTo(2)
        .jsonPath("$.completedRequests").isEqualTo(1)
        .jsonPath("$.completeRequestsWithDataHeld").isEqualTo(1)
    }
  }

  private fun createBacklogRequest(caseReference: String): BacklogRequest {
    val resp = webTestClient
      .post()
      .uri("/subject-access-request/backlog")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
      .bodyValue(
        CreateBacklogRequest(
          subjectName = "Jailbird, Snake",
          version = "1",
          sarCaseReferenceId = caseReference,
          nomisId = caseReference,
          ndeliusCaseReferenceId = "",
          dateFrom = LocalDate.now().minusYears(1),
          dateTo = LocalDate.now(),
        ),
      ).exchange()
      .expectStatus().isCreated
      .returnResult(BacklogRequest::class.java)
    return resp.responseBody.blockFirst()!!
  }
}
