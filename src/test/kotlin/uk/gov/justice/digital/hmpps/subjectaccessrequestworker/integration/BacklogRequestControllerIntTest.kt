package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import java.time.LocalDate

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
      _sarCaseReferenceId: String?,
      _nomisId: String?,
      _ndeliusCaseReferenceId: String?,
      _expectedMessage: String,
      description: String,
    ) {
      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .bodyValue(
          CreateBacklogRequest(
            sarCaseReferenceId = _sarCaseReferenceId,
            ndeliusCaseReferenceId = _nomisId,
            nomisId = _ndeliusCaseReferenceId,
            dateFrom = LocalDate.now().minusYears(1),
            dateTo = LocalDate.now(),
          ),
        ).exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.developerMessage")
        .isEqualTo(_expectedMessage)
    }
  }
}
