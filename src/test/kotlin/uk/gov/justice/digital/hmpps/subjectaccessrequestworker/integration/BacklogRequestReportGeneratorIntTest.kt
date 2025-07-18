package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Disable the Backlog processing scheduled task - test will seed data directly into db.
 */
@TestPropertySource(
  properties = [
    "backlog-request.processor.enabled=false",
  ],
)
class BacklogRequestReportGeneratorIntTest : BaseBacklogRequestIntTest() {

  @Autowired
  private lateinit var backlogRequestService: BacklogRequestService

  private val createBacklogRequest = CreateBacklogRequest(
    subjectName = "Jailbird, Snake",
    version = "1",
    sarCaseReferenceNumber = sarCaseRef,
    ndeliusCaseReferenceId = null,
    nomisId = testNomisId,
    dateFrom = dateFrom,
    dateTo = dateTo,
  )

  private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  override fun customSetup() {
    // No custom behaviour to implement.
  }

  @Test
  fun `should return status 401 when no auth head provided`() {
    webTestClient.get()
      .uri("/subject-access-request/backlog/versions/1/report")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `should return status 403 when auth head does not contain the required roles`() {
    webTestClient.get()
      .uri("/subject-access-request/backlog/versions/1/report")
      .headers(setAuthorisation(roles = listOf("ROLE_BOB")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `should return status 404 when version does not exist`() {
    sendGetReportRequest("99")
      .expectStatus().isNotFound
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("backlog version 99 not found")
  }

  @Test
  fun `should return status 400 when version does not have status COMPLETE`() {
    val response = postBacklogRequestExpectSuccess(createBacklogRequest)

    sendGetReportRequest(response.version)
      .expectStatus().isBadRequest
      .expectBody()
      .jsonPath("$.userMessage").isEqualTo("backlog version: ${response.version} is not COMPLETE")
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "nomis-1 | ''        | true  | true  | true  | true",
      "nomis-1 | ''        | true  | true  | false | true",
      "nomis-1 | ''        | true  | true  | false | false",
      "nomis-1 | ''        | true  | true  | true  | false",
      "nomis-1 | ''        | false | false | false | false",
      "nomis-1 | ''        | true  | false | false | true",
      "nomis-1 | ''        | true  | false | true  | false",
      "nomis-1 | ''        | true  | false | true  | true",
      " ''     | ndelius-1 | true  | true  | true  | true",
      " ''     | ndelius-1 | true  | true  | true  | true",
      " ''     | ndelius-1 | true  | true  | false | true",
      " ''     | ndelius-1 | true  | true  | false | false",
      " ''     | ndelius-1 | true  | true  | true  | false",
      " ''     | ndelius-1 | false | false | false | false",
      " ''     | ndelius-1 | true  | false | false | true",
      " ''     | ndelius-1 | true  | false | true  | false",
      " ''     | ndelius-1 | true  | false | true  | true",
    ],
    delimiterString = "|",
  )
  fun `should generated the correct CSV report for backlog request data`(
    nomisId: String?,
    ndeliusId: String?,
    expectDataHeld: Boolean,
    expectService1DataHeld: Boolean,
    expectService2DataHeld: Boolean,
    expectService3DataHeld: Boolean,
  ) {
    val request = seedBacklogRequestTestData(
      request = BacklogRequest(
        sarCaseReferenceNumber = sarCaseRef,
        subjectName = "Jailbird Snake",
        version = "1",
        nomisId = nomisId,
        ndeliusCaseReferenceId = ndeliusId,
        dateFrom = dateFrom,
        dateTo = dateTo,
        createdAt = LocalDateTime.now(),
      ),
      serviceDataHeld = arrayOf(
        expectService1DataHeld,
        expectService2DataHeld,
        expectService3DataHeld,
      ),
    )

    val csv: Csv = sendGetReportRequest(request.version)
      .expectStatus().isOk
      .expectBody()
      .returnResult()
      .toCsv()

    csv.assertTotalNumberOfRows(expected = 2)
    csv.assertHeaderContainsExpectedValues()
    csv.assertDataRowContainsExpectedValues(
      rowIndex = 1,
      expectedBacklogRequestId = request.id,
      expectedSarCaseReferenceNumber = sarCaseRef,
      expectedSubjectName = "Jailbird Snake",
      expectedNomisId = nomisId,
      expectedNdeliusCaseReferenceId = ndeliusId,
      expectedDateFrom = dateFormat.format(dateFrom),
      expectedDateTo = dateFormat.format(dateTo),
      expectedDataHeld = expectDataHeld,
      expectService1DataHeld = expectService1DataHeld,
      expectService2DataHeld = expectService2DataHeld,
      expectService3DataHeld = expectService3DataHeld,
    )
  }

  private fun seedBacklogRequestTestData(
    request: BacklogRequest,
    serviceDataHeld: Array<Boolean>,
  ): BacklogRequest {
    backlogRequestService.newBacklogRequest(request)

    serviceConfigurations.forEachIndexed { i, cfg ->
      backlogRequestService.addServiceSummary(
        request = request,
        summary = ServiceSummary(
          backlogRequest = request,
          serviceConfiguration = cfg,
          dataHeld = serviceDataHeld[i],
          status = BacklogRequestStatus.COMPLETE,
        ),
      )
    }

    backlogRequestService.attemptCompleteRequest(request.id)

    val result = backlogRequestService.getByIdOrNull(request.id)
    assertThat(result).isNotNull
    assertThat(result?.serviceSummary).isNotNull()
    assertThat(result!!.serviceSummary).hasSize(serviceConfigurations.size)
    assertThat(result.status).isEqualTo(BacklogRequestStatus.COMPLETE)

    return result
  }

  private fun sendGetReportRequest(version: String): WebTestClient.ResponseSpec = webTestClient
    .get()
    .uri("/subject-access-request/backlog/versions/$version/report")
    .headers(setAuthorisation(roles = listOf("ROLE_SAR_SUPPORT")))
    .exchange()

  fun EntityExchangeResult<ByteArray>.toCsv(): Csv {
    assertThat(this.responseBody).isNotNull()
    val bodyString = String(this.responseBody!!).trimIndent()
    return Csv(bodyString)
  }

  data class Csv(val raw: String) {
    private val data: List<Array<String>> = raw.split("\n")
      .map { row ->
        row.split(",").toTypedArray()
      }

    private val header: Array<String> = data[0]
    private val dataRows: Array<Array<String>> = data.subList(1, data.size).toTypedArray()

    fun assertTotalNumberOfRows(expected: Int) {
      assertThat(data).hasSize(expected)
    }

    fun assertHeaderContainsExpectedValues() {
      val expectedHeaders = listOf(
        "ID",
        "SAR Case Reference Number",
        "Subject Name",
        "Nomis Id",
        "Ndelius Case Reference Id",
        "Date From",
        "Date To",
        "Query Completed at",
        "Data Held",
        "Service 1",
        "Service 2",
        "Service 3",
      )

      assertThat(header).hasSize(expectedHeaders.size)

      expectedHeaders.forEachIndexed { headerIndex, expectedValue ->
        assertThat(header[headerIndex]).isEqualTo(expectedValue)
      }
    }

    fun assertDataRowContainsExpectedValues(
      rowIndex: Int,
      expectedBacklogRequestId: UUID,
      expectedSarCaseReferenceNumber: String,
      expectedSubjectName: String,
      expectedNomisId: String?,
      expectedNdeliusCaseReferenceId: String?,
      expectedDateFrom: String,
      expectedDateTo: String,
      expectedDataHeld: Boolean,
      expectService1DataHeld: Boolean,
      expectService2DataHeld: Boolean,
      expectService3DataHeld: Boolean,
    ) {
      val targetRow = data[rowIndex]
      var columnIndex = 0
      assertThat(targetRow[columnIndex++]).isEqualTo(expectedBacklogRequestId.toString())
      assertThat(targetRow[columnIndex++]).isEqualTo(expectedSarCaseReferenceNumber)
      assertThat(targetRow[columnIndex++]).isEqualTo(expectedSubjectName)
      assertThat(targetRow[columnIndex++]).isEqualTo(expectedNomisId)
      assertThat(targetRow[columnIndex++]).isEqualTo(expectedNdeliusCaseReferenceId)
      assertThat(targetRow[columnIndex++]).isEqualTo(expectedDateFrom)
      assertThat(targetRow[columnIndex++]).isEqualTo(expectedDateTo)
      assertThat(targetRow[columnIndex++]).isNotEmpty()
      assertThat(targetRow[columnIndex++]).isEqualTo(expectedDataHeld.toString())
      assertThat(targetRow[columnIndex++]).isEqualTo(expectService1DataHeld.toString())
      assertThat(targetRow[columnIndex++]).isEqualTo(expectService2DataHeld.toString())
      assertThat(targetRow[columnIndex]).isEqualTo(expectService3DataHeld.toString())
    }
  }
}
