package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.capture
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting.AlertsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.expectedSubjectAccessRequestParameters
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testNomisId
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.toGetParams
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.TestCase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.GetSubjectAccessRequestParams
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceOneApiExtension.Companion.serviceOneMockApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status.Completed
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status.Pending
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils.TemplateTestingUtil.Companion.getFormattedReportGenerationDate
import java.util.concurrent.TimeUnit

@TestPropertySource(
  properties = [
    "G1-api.url=http://localhost:4100",
    "G2-api.url=http://localhost:4100",
    "G3-api.url=http://localhost:4100",
  ],
)
class SubjectAccessRequestProcessorIntTest : BaseProcessorIntTest() {

  @MockitoBean
  private lateinit var alertsService: AlertsService

  @MockitoBean
  protected lateinit var dateService: DateService

  @Captor
  protected lateinit var errorCaptor: ArgumentCaptor<SubjectAccessRequestException>

  @BeforeEach
  fun setup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    clearDatabaseData()
    populatePrisonDetails()
    populateLocationDetails()

    /** Ensure the test generated reports have the same 'report generation date' as pre-generated reference reports */
    whenever(dateService.reportGenerationDate()).thenReturn(getFormattedReportGenerationDate())
  }

  @AfterEach
  fun cleanup() {
    clearDatabaseData()
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
  }

  @Nested
  inner class ReportGenerationSuccessScenarios {

    @ParameterizedTest
    @MethodSource("uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture#generateReportTestCases")
    fun `should process pending request successfully when data is held`(testCase: TestCase) {
      val sar = insertSubjectAccessRequest(testCase.serviceName, Pending)

      hmppsAuth.stubGrantToken()
      hmppsServiceReturnsSarData(testCase.serviceName, sar)
      prisonApi.stubGetOffenderDetails(sar.nomisId!!)
      documentApi.stubUploadFileSuccess(sar)

      await()
        .atMost(10, TimeUnit.SECONDS)
        .until { requestHasStatus(sar, Completed) }

      hmppsAuth.verifyCalledOnce()
      serviceOneMockApi.verifyApiCalled(1)
      documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())
      verifyNoInteractions(alertsService)

      assertUploadedDocumentMatchesExpectedPdf(testCase.serviceName)
      assertSubjectAccessRequestHasStatus(sar, Completed)
    }

    @ParameterizedTest
    @MethodSource("uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture#generateReportTestCases")
    fun `should process pending request successfully when no data is held`(testCase: TestCase) {
      val sar = insertSubjectAccessRequest(testCase.serviceName, Pending)

      hmppsAuth.stubGrantToken()
      hmppsServiceReturnsNoSarData()
      prisonApi.stubGetOffenderDetails(sar.nomisId!!)
      documentApi.stubUploadFileSuccess(sar)

      await()
        .atMost(10, TimeUnit.SECONDS)
        .until { requestHasStatus(sar, Completed) }

      hmppsAuth.verifyCalledOnce()
      serviceOneMockApi.verifyApiCalled(1)
      documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())
      verifyNoInteractions(alertsService)

      assertUploadedDocumentMatchesExpectedNoDataHeldPdf(testCase)
      assertSubjectAccessRequestHasStatus(sar, Completed)
    }
  }

  @Nested
  inner class ReportGenerationErrorScenarios {

    @Test
    fun `should fail to process request when service does not exist`() {
      val serviceName = "this-service-does-not-exist"
      val sar = insertSubjectAccessRequest(serviceName, Pending)

      await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted {
          verify(alertsService, times(1)).raiseReportErrorAlert(capture(errorCaptor))
        }

      assertThat(errorCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)

      val actual = errorCaptor.allValues[0]
      assertThat(actual.message).startsWith("subjectAccessRequest failed with non-retryable error: service with name '$serviceName' not found")
      assertThat(actual.subjectAccessRequest?.id).isEqualTo(sar.id)
      assertThat(actual.event).isEqualTo(ProcessingEvent.GET_SERVICE_CONFIGURATION)
      assertThat(actual.cause).isNull()

      assertRequestClaimedAtLeastOnce(sar)

      hmppsAuth.verifyNeverCalled()
      documentApi.verifyNeverCalled()
      serviceOneMockApi.verifyNeverCalled()
      prisonApi.verifyNeverCalled()
    }

    @Test
    fun `should throw exception if request to service API is unsuccessful`() {
      val serviceName = "keyworker-api"
      val sar = insertSubjectAccessRequest(serviceName, Pending)

      hmppsAuth.stubGrantToken()
      hmppsServiceReturnsStatus500(sar)

      await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted {
          verify(alertsService, times(1)).raiseReportErrorAlert(capture(errorCaptor))
        }

      assertThat(errorCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
      val actual = errorCaptor.allValues[0]

      assertThat(actual.message).startsWith("subjectAccessRequest failed and max retry attempts (2) exhausted")
      assertThat(actual.subjectAccessRequest?.id).isEqualTo(sar.id)
      assertThat(actual.event).isEqualTo(ProcessingEvent.GET_SAR_DATA)
      assertThat(actual.params).containsExactlyEntriesOf(mapOf("uri" to "http://localhost:4100"))

      assertRequestClaimedAtLeastOnce(sar)

      hmppsAuth.verifyCalledOnce()
      serviceOneMockApi.verifyApiCalled(3)
      documentApi.verifyNeverCalled()
      prisonApi.verifyNeverCalled()
    }

    @Test
    fun `should throw exception if document api request is unsuccessful`() {
      val serviceName = "hmpps-book-secure-move-api"
      val sar = insertSubjectAccessRequest(serviceName, Pending)

      hmppsAuth.stubGrantToken()
      hmppsServiceReturnsSarData(serviceName, sar)
      documentApi.stubUploadFileFailsWithStatus(sar.id.toString(), 500)

      await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted {
          verify(alertsService, times(1)).raiseReportErrorAlert(capture(errorCaptor))
        }

      assertThat(errorCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
      val actual = errorCaptor.allValues[0]

      assertThat(actual.message).startsWith("subjectAccessRequest failed and max retry attempts (2) exhausted")
      assertThat(actual.subjectAccessRequest?.id).isEqualTo(sar.id)
      assertThat(actual.event).isEqualTo(ProcessingEvent.STORE_DOCUMENT)
      assertThat(actual.params).containsExactlyEntriesOf(mapOf("uri" to "/documents/SUBJECT_ACCESS_REQUEST_REPORT/${sar.id}"))
      assertThat(actual.cause).isNotNull()
      assertThat(actual.cause!!.message).contains("500 Internal Server Error from POST http://localhost:${documentApi.port()}/documents/SUBJECT_ACCESS_REQUEST_REPORT/${sar.id}")

      hmppsAuth.verifyCalledOnce()
      serviceOneMockApi.verifyApiCalled(1)
      prisonApi.verifyApiCalled(1, sar.nomisId!!)
      documentApi.verifyStoreDocumentIsCalled(3, sar.id.toString())
    }
  }

  private fun hmppsServiceReturnsSarData(serviceName: String, subjectAccessRequest: SubjectAccessRequest) {
    val responseBody = getSarResponseStub("$serviceName-stub.json")

    serviceOneMockApi.stubResponseFor(
      response = ResponseDefinitionBuilder()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(responseBody),
      params = subjectAccessRequest.toGetParams(),
    )
  }

  fun hmppsServiceReturnsNoSarData() {
    serviceOneMockApi.stubResponseFor(
      ResponseDefinitionBuilder()
        .withStatus(204)
        .withHeader("Content-Type", "application/json"),
      expectedSubjectAccessRequestParameters,
    )
  }

  private fun hmppsServiceReturnsStatus500(subjectAccessRequest: SubjectAccessRequest) {
    serviceOneMockApi.stubSubjectAccessRequestErrorResponse(
      status = 500,
      params = GetSubjectAccessRequestParams(
        prn = subjectAccessRequest.nomisId,
        crn = subjectAccessRequest.ndeliusCaseReferenceId,
        dateFrom = subjectAccessRequest.dateFrom,
        dateTo = subjectAccessRequest.dateTo,
      ),
    )
  }

  fun assertUploadedDocumentMatchesExpectedNoDataHeldPdf(testCase: TestCase) {
    val actual = getUploadedPdfDocument()

    assertThat(actual.numberOfPages).isEqualTo(5)
    val actualPageContent = PdfTextExtractor.getTextFromPage(actual.getPage(4), SimpleTextExtractionStrategy())
    val expectedPageContent = contentWhenNoDataHeld(testCase.serviceLabel, testNomisId)

    assertThat(actualPageContent)
      .isEqualTo(expectedPageContent)
      .withFailMessage("${testCase.serviceName} report did not match expected")
  }

  private fun contentWhenNoDataHeld(serviceLabel: String, nomidId: String): String = StringBuilder("$serviceLabel ")
    .append("\n")
    .append("No Data Held")
    .append("\n")
    .append("Name: REACHER, Joe ")
    .append("\n")
    .append("NOMIS ID: $nomidId")
    .append("\n")
    .append("Official Sensitive")
    .toString()
}
