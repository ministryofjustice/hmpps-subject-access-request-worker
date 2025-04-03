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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceOneApiExtension.Companion.serviceOneMockApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
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
class SubjectAccessRequestProcessorIntTest : IntegrationTestBase() {

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
      val sar = createSubjectAccessRequestWithStatus(Pending, testCase.serviceName)
      assertSubjectAccessRequestHasStatus(sar, Pending)

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
      val sar = createSubjectAccessRequestWithStatus(Pending, testCase.serviceName)
      assertSubjectAccessRequestHasStatus(sar, Pending)

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
      val sar = createSubjectAccessRequestWithStatus(Pending, serviceName)
      assertSubjectAccessRequestHasStatus(sar, Pending)

      await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted {
          verify(alertsService, times(1)).raiseReportErrorAlert(capture(errorCaptor))
        }

      assertThat(errorCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)

      val actualException = errorCaptor.allValues[0]
      assertThat(actualException.message).startsWith("subjectAccessRequest failed with non-retryable error: service with name '$serviceName' not found")
      assertThat(actualException.subjectAccessRequest?.id).isEqualTo(sar.id)
      assertThat(actualException.event).isEqualTo(ProcessingEvent.GET_SERVICE_CONFIGURATION)

      assertRequestClaimedAtLeastOnce(sar)

      hmppsAuth.verifyNeverCalled()
      documentApi.verifyNeverCalled()
      serviceOneMockApi.verifyNeverCalled()
      prisonApi.verifyNeverCalled()
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

  fun assertUploadedDocumentMatchesExpectedPdf(serviceName: String) {
    val expected = getPreGeneratedPdfDocument("$serviceName-reference.pdf")
    val actual = getUploadedPdfDocument()

    assertThat(actual.numberOfPages).isEqualTo(expected.numberOfPages)

    for (i in 1..actual.numberOfPages) {
      val actualPageN = PdfTextExtractor.getTextFromPage(actual.getPage(i), SimpleTextExtractionStrategy())
      val expectedPageN = PdfTextExtractor.getTextFromPage(expected.getPage(i), SimpleTextExtractionStrategy())

      assertThat(actualPageN)
        .isEqualTo(expectedPageN)
        .withFailMessage("actual page: $i did not match expected.")
    }
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

  private fun requestHasStatus(subjectAccessRequest: SubjectAccessRequest, expectedStatus: Status): Boolean {
    val target = getSubjectAccessRequest(subjectAccessRequest.id)
    return expectedStatus == target.status
  }

  private fun assertRequestClaimedAtLeastOnce(subjectAccessRequest: SubjectAccessRequest) {
    val target = getSubjectAccessRequest(subjectAccessRequest.id)
    assertThat(target.claimDateTime).isNotNull()
    assertThat(target.claimAttempts).isGreaterThanOrEqualTo(1)
  }

  private fun clearDatabaseData() {
    subjectAccessRequestRepository.deleteAll()
    subjectAccessRequestRepository.deleteAll()
    prisonDetailsRepository.deleteAll()
    locationDetailsRepository.deleteAll()
  }

  private fun populatePrisonDetails() {
    prisonDetailsRepository.saveAndFlush(PrisonDetail("MDI", "MOORLAND (HMP & YOI)"))
    prisonDetailsRepository.saveAndFlush(PrisonDetail("LEI", "LEEDS (HMP)"))
  }

  private fun populateLocationDetails() {
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "cac85758-380b-49fc-997f-94147e2553ac",
        357591,
        "ASSO A WING",
      ),
    )
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "d0763236-c073-4ef4-9592-419bf0cd72cb",
        357592,
        "ASSO B WING",
      ),
    )
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "8ac39ebb-499d-4862-ae45-0b091253e89d",
        27187,
        "ADJ",
      ),
    )
  }
}
