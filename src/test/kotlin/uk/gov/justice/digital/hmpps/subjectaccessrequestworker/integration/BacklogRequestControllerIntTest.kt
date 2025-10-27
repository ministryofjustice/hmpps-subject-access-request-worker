package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestOverview
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceSummaryRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ServiceConfigurationService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class BacklogRequestControllerIntTest : IntegrationTestBase() {

  private companion object {
    const val SAR_CASE_REF_INVALID_MSG = "non null/empty value is required for sarCaseReferenceNumber"
    const val NOMIS_NDELIUS_ID_INVALID_MSG = "a non null/empty value is required for nomisId or ndeliusCaseReferenceId"
    const val MULTIPLE_SUBJECT_IDS_PROVIDED_MSG =
      "multiple ID's provided provided please provide either a nomisId or ndeliusCaseReferenceId"
    const val SUBJECT_NAME_NULL_OR_EMPTY_MSG = "a non null/empty value is required for subject name"
    const val VERSION_NULL_OR_EMPTY_MSG = "a non null/empty value is required for version"
  }

  @Autowired
  lateinit var backlogRequestService: BacklogRequestService

  @Autowired
  lateinit var serviceConfigurationService: ServiceConfigurationService

  @Autowired
  lateinit var serviceSummaryRepository: ServiceSummaryRepository

  val createBacklogRequest1 = CreateBacklogRequest(
    subjectName = "Jailbird, Snake",
    version = "1",
    sarCaseReferenceNumber = "test-001",
    ndeliusCaseReferenceId = null,
    nomisId = "nomis-1",
    dateFrom = LocalDate.now().minusYears(1),
    dateTo = LocalDate.now(),
  )

  val createBacklogRequest2 = CreateBacklogRequest(
    subjectName = "Tony, Fat",
    version = "2",
    sarCaseReferenceNumber = "test-002",
    ndeliusCaseReferenceId = null,
    nomisId = "nomis-2",
    dateFrom = LocalDate.now().minusYears(1),
    dateTo = LocalDate.now(),
  )

  val createBacklogRequest3 = CreateBacklogRequest(
    subjectName = "Bob, Sideshow",
    version = "1",
    sarCaseReferenceNumber = "test-003",
    ndeliusCaseReferenceId = null,
    nomisId = "nomis-3",
    dateFrom = LocalDate.now().minusYears(1),
    dateTo = LocalDate.now(),
  )

  val service1Config = ServiceConfiguration(
    serviceName = "service-1",
    order = 1,
    label = "service-1",
    url = "http://localhost:8080",
    enabled = true,
  )

  lateinit var backlogReqOneId: UUID
  lateinit var backlogReqTwoId: UUID
  lateinit var backlogReqThreeId: UUID

  @BeforeEach
  fun setup() {
    backlogRequestService.deleteAll()
    backlogReqOneId = postBacklogRequestExpectSuccess(createBacklogRequest1).id
    backlogReqTwoId = postBacklogRequestExpectSuccess(createBacklogRequest2).id
    backlogReqThreeId = postBacklogRequestExpectSuccess(createBacklogRequest3).id

    serviceConfigurationService.deleteAll()
    serviceConfigurationService.save(service1Config)
  }

  @Nested
  inner class GetBacklogRequestVersionsTestCases {

    @Test
    fun `should return status 401 if no auth header is present`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/versions")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return status 403 if auth header does not contain the required role`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/versions")
        .headers(setAuthorisation(roles = listOf("ROLE_BOB")))
        .exchange()
        .expectStatus()
    }

    @Test
    fun `should return empty result when no versions exist`() {
      backlogRequestService.deleteAll()

      webTestClient.get()
        .uri("/subject-access-request/backlog/versions")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.versions").isArray
        .jsonPath("$.versions.length()").isEqualTo(0)
    }

    @Test
    fun `should return expected result when versions exist`() {
      webTestClient.get()
        .uri("/subject-access-request/backlog/versions")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.versions").isArray
        .jsonPath("$.versions.length()").isEqualTo(2)
        .jsonPath("$.versions[0]").isEqualTo("1")
        .jsonPath("$.versions[1]").isEqualTo("2")
    }
  }

  @Nested
  inner class GetBacklogRequestVersionStatusTestCases {

    @Test
    fun `should return status 401 if no auth header is present`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/versions/1")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return status 403 if auth header does not contain the required role`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/versions/1")
        .headers(setAuthorisation(roles = listOf("ROLE_BOB")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return status 404 for backlog requests status version does not exist`() {
      webTestClient.get()
        .uri("/subject-access-request/backlog/versions/99")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return backlog requests status success`() {
      val backlogRequest = getBacklogRequestById(backlogReqOneId)
      backlogRequest.status = BacklogRequestStatus.COMPLETE
      backlogRequest.dataHeld = true
      backlogRequestService.newBacklogRequest(backlogRequest)

      webTestClient.get()
        .uri("/subject-access-request/backlog/versions/1")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.totalRequests").isEqualTo(2)
        .jsonPath("$.pendingRequests").isEqualTo(1)
        .jsonPath("$.completedRequests").isEqualTo(1)
        .jsonPath("$.completeRequestsWithDataHeld").isEqualTo(1)
        .jsonPath("$.status").isEqualTo("IN_PROGRESS")
    }
  }

  @Nested
  inner class GetBacklogRequestsByVersionTestCases {

    @Test
    fun `should return status 401 if no auth header is present`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/versions/1/requests")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return status 403 if auth header does not contain the required role`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/versions/1/requests")
        .headers(setAuthorisation(roles = listOf("ROLE_BOB")))
        .exchange()
        .expectStatus()
    }

    @Test
    fun `should return 404 when requested version does not exist`() {
      backlogRequestService.deleteAll()

      webTestClient
        .get()
        .uri("/subject-access-request/backlog/versions/666/requests")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return expected result when requests exist`() {
      val requestOne = getBacklogRequestById(backlogReqOneId)
      val requestThree = getBacklogRequestById(backlogReqThreeId)
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/versions/1/requests")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$.[0].id").isEqualTo(requestOne.id)
        .jsonPath("$.[0].subjectName").isEqualTo(requestOne.subjectName)
        .jsonPath("$.[0].version").isEqualTo("1")
        .jsonPath("$.[0].sarCaseReferenceNumber").isEqualTo(requestOne.sarCaseReferenceNumber)
        .jsonPath("$.[0].nomisId").isEqualTo(requestOne.nomisId)
        .jsonPath("$.[0].ndeliusCaseReferenceId").isEqualTo(requestOne.ndeliusCaseReferenceId)
        .jsonPath("$.[0].status").isEqualTo(BacklogRequestStatus.PENDING)
        .jsonPath("$.[0].createdDate").isNotEmpty
        .jsonPath("$.[0].dateFrom").isEqualTo(requestOne.dateFrom)
        .jsonPath("$.[0].dateTo").isEqualTo(requestOne.dateTo)
        .jsonPath("$.[1].id").isEqualTo(requestThree.id)
        .jsonPath("$.[1].subjectName").isEqualTo(requestThree.subjectName)
        .jsonPath("$.[1].version").isEqualTo("1")
        .jsonPath("$.[1].sarCaseReferenceNumber").isEqualTo(requestThree.sarCaseReferenceNumber)
        .jsonPath("$.[1].nomisId").isEqualTo(requestThree.nomisId)
        .jsonPath("$.[1].ndeliusCaseReferenceId").isEqualTo(requestThree.ndeliusCaseReferenceId)
        .jsonPath("$.[1].status").isEqualTo(BacklogRequestStatus.PENDING)
        .jsonPath("$.[1].createdDate").isNotEmpty
        .jsonPath("$.[1].dateFrom").isEqualTo(requestThree.dateFrom)
        .jsonPath("$.[1].dateTo").isEqualTo(requestThree.dateTo)
    }
  }

  @Nested
  inner class GetBacklogRequestByIdTestCases {

    @Test
    fun `should return status 401 if no auth header is present`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/$backlogReqOneId")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return status 403 if auth header does not contain the required role`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/$backlogReqOneId")
        .headers(setAuthorisation(roles = listOf("ROLE_BOB")))
        .exchange()
        .expectStatus()
    }

    @Test
    fun `should return 404 when no requests exist`() {
      webTestClient
        .get()
        .uri("/subject-access-request/backlog/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isNotFound
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "true   | dataHeld is true when at least 1 service summary has dataHeld true",
        "false  | dataHeld is false when no service summaries exist with dataHeld true",
      ],
      delimiterString = "|",
    )
    fun `should return expected result when requests exist and data is not held`(
      expectDataHeld: String,
      description: String,
    ) {
      val backlogRequest = getBacklogRequestById(backlogReqOneId)
      val summary1 = ServiceSummary(
        id = UUID.randomUUID(),
        backlogRequest = backlogRequest,
        serviceConfiguration = service1Config,
        dataHeld = expectDataHeld.toBoolean(),
        status = BacklogRequestStatus.COMPLETE,
      )

      backlogRequestService.addServiceSummary(backlogRequest!!, summary1)

      webTestClient
        .get()
        .uri("/subject-access-request/backlog/$backlogReqOneId")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(backlogReqOneId)
        .jsonPath("$.sarCaseReferenceNumber").isEqualTo(backlogRequest!!.sarCaseReferenceNumber)
        .jsonPath("$.nomisId").isEqualTo(backlogRequest!!.nomisId)
        .jsonPath("$.status").isEqualTo(backlogRequest.status.name)
        .jsonPath("$.createdDate").isNotEmpty
        .jsonPath("$.subjectName").isEqualTo(backlogRequest.subjectName)
        .jsonPath("$.version").isEqualTo(backlogRequest.version)
        .jsonPath("$.dataHeld").isEqualTo(expectDataHeld)
        .jsonPath("$.serviceSummary").isArray
        .jsonPath("$.serviceSummary.length()").isEqualTo(1)
        .jsonPath("$.serviceSummary.[0].serviceName").isEqualTo("service-1")
        .jsonPath("$.serviceSummary.[0].processingStatus").isEqualTo("COMPLETE")
        .jsonPath("$.serviceSummary.[0].dataHeld").isEqualTo(expectDataHeld)
    }
  }

  @Nested
  inner class CreateBacklogRequestTestCases {

    @Test
    fun `should return status 401 if no auth header is present`() {
      backlogRequestService.deleteAll()

      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .bodyValue(createBacklogRequest1)
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return status 403 if auth header does not contain the required role`() {
      backlogRequestService.deleteAll()

      webTestClient
        .post()
        .uri("/subject-access-request/backlog")
        .headers(setAuthorisation(roles = listOf("ROLE_BOB")))
        .bodyValue(createBacklogRequest2)
        .exchange()
        .expectStatus()
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "    |    |    |      |    | $SAR_CASE_REF_INVALID_MSG          | sarCaseReferenceNumber is null",
        " '' |    |    |      |    | $SAR_CASE_REF_INVALID_MSG          | sarCaseReferenceNumber is empty",
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
      sarCaseReferenceNumber: String?,
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
            sarCaseReferenceNumber = sarCaseReferenceNumber,
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
        sarCaseReferenceNumber = "test-001",
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
        .jsonPath("$.sarCaseReferenceNumber").isEqualTo(request.sarCaseReferenceNumber!!)
        .jsonPath("$.nomisId").isEqualTo(nomisId)
        .jsonPath("$.ndeliusCaseReferenceId").isEqualTo(ndeliusId)
        .jsonPath("$.status").isEqualTo("PENDING")
        .jsonPath("$.createdDate").value<String> {
          LocalDateTime.parse(it).isAfter(startOfTest)
          LocalDateTime.parse(it).isBefore(LocalDateTime.now())
        }
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        " sar1 | nomis1 | v1 | 2000-01-01 | 2025-01-01 | 409 | duplicate sarCaseReferenceId, nomisId, version, dateFrom and dateTo values",
        " sar2 | nomis1 | v1 | 2000-01-01 | 2025-01-01 | 201 | unique sarCaseReferenceId, duplicate nomisId, version, dateFrom and dateTo values",
        " sar1 | nomis2 | v1 | 2000-01-01 | 2025-01-01 | 201 | unique nomisId, duplicate sarCaseReferenceId, version, dateFrom and dateTo values",
        " sar1 | nomis1 | v2 | 2000-01-01 | 2025-01-01 | 201 | unique Version, duplicate sarCaseReferenceId, nomisId, dateFrom and dateTo values",
        " sar1 | nomis1 | v1 | 2000-01-02 | 2025-01-01 | 201 | unique dateFrom, duplicate sarCaseReferenceId, nomisId, Version and dateTo values",
        " sar1 | nomis1 | v1 | 2000-01-01 | 2025-01-02 | 201 | unique dateTo, duplicate sarCaseReferenceId, nomisId, Version and dateFrom values",
      ],
      delimiterString = "|",
    )
    fun `create request respects the unique constraint on sarCaseReferenceId, nomisId, version, dateFrom and dateTo values`(
      sarCaseReferenceNumber: String,
      nomisId: String?,
      version: String,
      dateFromStr: String,
      dateToStr: String,
      expectedStatus: Int,
      description: String,
    ) {
      // Send original create request.
      postBacklogRequest(
        CreateBacklogRequest(
          subjectName = "Jailbird, Snake",
          version = "v1",
          sarCaseReferenceNumber = "sar1",
          nomisId = "nomis1",
          ndeliusCaseReferenceId = null,
          dateFrom = LocalDate.of(2000, 1, 1),
          dateTo = LocalDate.of(2025, 1, 1),
        ),
      ).expectStatus().isCreated

      // Send 2nd create request.
      postBacklogRequest(
        CreateBacklogRequest(
          subjectName = "Jailbird, Snake",
          version = version,
          sarCaseReferenceNumber = sarCaseReferenceNumber,
          nomisId = nomisId,
          ndeliusCaseReferenceId = null,
          dateFrom = LocalDate.parse(dateFromStr),
          dateTo = LocalDate.parse(dateToStr),
        ),
      ).expectStatus().isEqualTo(expectedStatus)
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        " sar1 | ndelius1 | v1 | 2000-01-01 | 2025-01-01 | 409 | duplicate sarCaseReferenceId, ndeliusId, version, dateFrom and dateTo values",
        " sar2 | ndelius1 | v1 | 2000-01-01 | 2025-01-01 | 201 | unique sarCaseReferenceId, duplicate ndeliusId, version, dateFrom and dateTo values",
        " sar1 | ndelius2 | v1 | 2000-01-01 | 2025-01-01 | 201 | unique ndeliusId, duplicate sarCaseReferenceId, version, dateFrom and dateTo values",
        " sar1 | ndelius1 | v2 | 2000-01-01 | 2025-01-01 | 201 | unique Version, duplicate sarCaseReferenceId, ndeliusId, dateFrom and dateTo values",
        " sar1 | ndelius1 | v1 | 2000-01-02 | 2025-01-01 | 201 | unique dateFrom, duplicate sarCaseReferenceId, ndeliusId, Version and dateTo values",
        " sar1 | ndelius1 | v1 | 2000-01-01 | 2025-01-02 | 201 | unique dateTo, duplicate sarCaseReferenceId, ndeliusId, Version and dateFrom values",
      ],
      delimiterString = "|",
    )
    fun `create request respects the unique constraint on sarCaseReferenceId, ndeliusCaseReferenceNumber, version, dateFrom and dateTo values`(
      sarCaseReferenceNumber: String,
      ndeliusCaseReferenceNumber: String?,
      version: String,
      dateFromStr: String,
      dateToStr: String,
      expectedStatus: Int,
      description: String,
    ) {
      // Send original create request.
      postBacklogRequest(
        CreateBacklogRequest(
          subjectName = "Jailbird, Snake",
          version = "v1",
          sarCaseReferenceNumber = "sar1",
          nomisId = null,
          ndeliusCaseReferenceId = "ndelius1",
          dateFrom = LocalDate.of(2000, 1, 1),
          dateTo = LocalDate.of(2025, 1, 1),
        ),
      ).expectStatus().isCreated

      // Send 2nd create request.
      postBacklogRequest(
        CreateBacklogRequest(
          subjectName = "Jailbird, Snake",
          version = version,
          sarCaseReferenceNumber = sarCaseReferenceNumber,
          nomisId = null,
          ndeliusCaseReferenceId = ndeliusCaseReferenceNumber,
          dateFrom = LocalDate.parse(dateFromStr),
          dateTo = LocalDate.parse(dateToStr),
        ),
      ).expectStatus().isEqualTo(expectedStatus)
    }

    @Test
    fun `should return status 400 when request dateTo precedes request dateFrom value`() {
      postBacklogRequest(
        CreateBacklogRequest(
          subjectName = "Jailbird, Snake",
          version = "v1",
          sarCaseReferenceNumber = "sar1",
          nomisId = "nomis1",
          ndeliusCaseReferenceId = null,
          dateFrom = LocalDate.of(2000, 1, 1),
          dateTo = LocalDate.of(1999, 1, 1),
        ),
      ).expectStatus().isBadRequest
        .expectBody()
        .jsonPath("$.developerMessage").isEqualTo("invalid request date range dateTo precedes dateFrom")
    }
  }

  @Nested
  inner class DeleteBacklogRequestsByVersionTestCases {

    @Test
    fun `should return bad request if backlog request ID does not exist`() {
      webTestClient
        .delete()
        .uri("/subject-access-request/backlog/versions/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `should delete all backlog requests and summaries by version`() {
      val version = "666"

      // Create Backlog request 1
      val idOne = postBacklogRequestExpectSuccess(
        CreateBacklogRequest(
          dateFrom = LocalDate.now().minusYears(1),
          dateTo = LocalDate.now(),
          subjectName = "Tatum, Drederick",
          version = version,
          sarCaseReferenceNumber = "test-666",
          nomisId = "666",
          ndeliusCaseReferenceId = null,
        ),
      ).id

      // Add service summary to request 1
      val requestOne = getBacklogRequestById(idOne)
      backlogRequestService.addServiceSummary(
        requestOne,
        ServiceSummary(
          id = UUID.randomUUID(),
          backlogRequest = requestOne,
          serviceConfiguration = service1Config,
          dataHeld = true,
          status = BacklogRequestStatus.COMPLETE,
        ),
      )

      // Create Backlog request 2
      val idTwo = postBacklogRequestExpectSuccess(
        CreateBacklogRequest(
          dateFrom = LocalDate.now().minusYears(1),
          dateTo = LocalDate.now(),
          subjectName = "Simpson, Homer",
          version = version,
          sarCaseReferenceNumber = "test-667",
          nomisId = "667",
          ndeliusCaseReferenceId = null,
        ),
      ).id

      // Add service summary to request 2
      val requestTwo = getBacklogRequestById(idTwo)
      backlogRequestService.addServiceSummary(
        requestTwo,
        ServiceSummary(
          id = UUID.randomUUID(),
          backlogRequest = requestTwo,
          serviceConfiguration = service1Config,
          dataHeld = true,
          status = BacklogRequestStatus.COMPLETE,
        ),
      )

      val targetIds = listOf(idOne, idTwo)

      targetIds.forEach { id ->
        val savedReqOne = backlogRequestService.getByIdOrNull(id)
        assertThat(savedReqOne).isNotNull
        assertThat(savedReqOne!!.serviceSummary).hasSize(1)
        assertThat(savedReqOne.version).isEqualTo(version)

        val summaries = serviceSummaryRepository.findByBacklogRequestId(id)
        assertThat(summaries).hasSize(1)
      }

      // Delete by version
      webTestClient
        .delete()
        .uri("/subject-access-request/backlog/versions/$version")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isOk
        .expectBody()
        .jsonPath("$.deleted").isEqualTo("2")

      // assert backlog request and summaries no longer exist
      targetIds.forEach { id ->
        assertThat(backlogRequestService.getByIdOrNull(id)).isNull()
        assertThat(serviceSummaryRepository.findByBacklogRequestId(id)).isEmpty()
      }
    }
  }

  @Nested
  inner class DeleteByIdTestCases {

    @Test
    fun `should return not found if backlog request ID does not exist`() {
      webTestClient
        .delete()
        .uri("/subject-access-request/backlog/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isNotFound
    }

    @Test
    fun `should delete backlog request and service summaries`() {
      val id = postBacklogRequestExpectSuccess(
        CreateBacklogRequest(
          dateFrom = LocalDate.now().minusYears(1),
          dateTo = LocalDate.now(),
          subjectName = "Tatum, Drederick",
          version = "1",
          sarCaseReferenceNumber = "test-666",
          nomisId = "666",
          ndeliusCaseReferenceId = null,
        ),
      ).id

      val requestOne = getBacklogRequestById(id)
      backlogRequestService.addServiceSummary(
        requestOne,
        ServiceSummary(
          id = UUID.randomUUID(),
          backlogRequest = requestOne,
          serviceConfiguration = service1Config,
          dataHeld = true,
          status = BacklogRequestStatus.COMPLETE,
        ),
      )

      val savedReqOne = backlogRequestService.getByIdOrNull(id)
      assertThat(savedReqOne).isNotNull
      assertThat(savedReqOne!!.serviceSummary).hasSize(1)

      val summaries = serviceSummaryRepository.findByBacklogRequestId(id)
      assertThat(summaries).hasSize(1)

      webTestClient
        .delete()
        .uri("/subject-access-request/backlog/$id")
        .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
        .exchange()
        .expectStatus()
        .isOk

      assertThat(backlogRequestService.getByIdOrNull(id)).isNull()
      assertThat(serviceSummaryRepository.findByBacklogRequestId(id)).isEmpty()
    }
  }

  private fun postBacklogRequestExpectSuccess(request: CreateBacklogRequest): BacklogRequestOverview {
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

  private fun postBacklogRequest(
    request: CreateBacklogRequest,
  ): WebTestClient.ResponseSpec = webTestClient
    .post()
    .uri("/subject-access-request/backlog")
    .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
    .bodyValue(request)
    .exchange()

  fun getBacklogRequestById(id: UUID): BacklogRequest {
    val backlogRequest = backlogRequestService.getByIdOrNull(id)
    assertThat(backlogRequest).isNotNull
    return backlogRequest!!
  }
}
