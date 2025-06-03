package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import java.time.LocalDate
import java.util.UUID

class BacklogRequestControllerIntTest : IntegrationTestBase() {

  @Nested
  inner class AuthenticationTestCases {
    @Test
    fun `should return status 401 if no auth header is present`() {
      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .bodyValue(
          CreateBacklogRequest(
            sarCaseReferenceId = "test-001",
            ndeliusCaseReferenceId = null,
            nomisId = null,
            dateFrom = LocalDate.now().minusYears(1),
            dateTo = LocalDate.now(),
          ),
        ).exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return status 403 if auth header does not contain the required role`() {
      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_DATA_ACCESS")))
        .bodyValue(
          CreateBacklogRequest(
            sarCaseReferenceId = "test-001",
            ndeliusCaseReferenceId = null,
            nomisId = null,
            dateFrom = LocalDate.now().minusYears(1),
            dateTo = LocalDate.now(),
          ),
        ).exchange()
        .expectStatus()
    }
  }

  @Nested
  inner class ValidationTestCases {

    @ParameterizedTest
    @CsvSource(
      value = [
        " | | | non null/empty value is required for sarCaseReferenceId | Test case: sarCaseReferenceId is null",
        " '' | | | non null/empty value is required for sarCaseReferenceId | Test case: sarCaseReferenceId is empty",
        "sarTestCase01 | | | a non null/empty value is required for nomisId or ndeliusCaseReferenceId | Test case: nomisId and ndeliusCaseReferenceId are null",
        "sarTestCase01 | '' | | a non null/empty value is required for nomisId or ndeliusCaseReferenceId | Test case: nomisId is empty ndeliusCaseReferenceId is null",
        "sarTestCase01 | '' | '' | a non null/empty value is required for nomisId or ndeliusCaseReferenceId | Test case: nomisId and ndeliusCaseReferenceId are empty",
        "sarTestCase01 | | '' | a non null/empty value is required for nomisId or ndeliusCaseReferenceId | Test case: nomisId is null ndeliusCaseReferenceId is empty",
      ],
      delimiterString = "|",
    )
    fun `should return status 400 if nomisId and ndeliusCaseReferenceId is null`(
      sarCaseReferenceId: String?,
      nomisId: String?,
      ndeliusCaseReferenceId: String?,
      expectedMessage: String,
      description: String,
    ) {
      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .bodyValue(
          CreateBacklogRequest(
            sarCaseReferenceId = sarCaseReferenceId,
            ndeliusCaseReferenceId = nomisId,
            nomisId = ndeliusCaseReferenceId,
            dateFrom = LocalDate.now().minusYears(1),
            dateTo = LocalDate.now(),
          ),
        ).exchange()
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
    }
  }

  private fun createBacklogRequest(caseReference: String): BacklogRequest {
    val resp = webTestClient
      .post()
      .uri("/subject-access-request/backlog")
      .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
      .bodyValue(
        CreateBacklogRequest(
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
