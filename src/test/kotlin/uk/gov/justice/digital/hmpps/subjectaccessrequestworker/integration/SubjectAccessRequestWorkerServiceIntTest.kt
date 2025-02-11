package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client.GetSubjectAccessRequestParams
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceOneApiExtension.Companion.serviceOneMockApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.SubjectAccessRequestWorkerService
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.util.UUID

class SubjectAccessRequestWorkerServiceIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var oAuth2AuthorizedClientService: OAuth2AuthorizedClientService

  @Autowired
  private lateinit var subjectAccessRequestWorkerService: SubjectAccessRequestWorkerService

  @Autowired
  private lateinit var prisonDetailsRepository: PrisonDetailsRepository

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling the documentStore API.
    oAuth2AuthorizedClientService.removeAuthorizedClient("sar-client", "anonymousUser")

    prisonDetailsRepository.saveAndFlush(PrisonDetail("MDI", "MOORLAND (HMP & YOI)"))
    prisonDetailsRepository.saveAndFlush(PrisonDetail("LEI", "LEEDS (HMP)"))

    hmppsAuth.stubGrantToken()
  }

  @ParameterizedTest
  @MethodSource("testCases")
  fun `SAR worker generates and uploads the expected PDF to the document store`(testCase: TestCase) {
    // Given
    val subjectAccessRequest = `A subject access request for service`(testCase.serviceName)

    // And
    `Subject Access Request service endpoint returns JSON data`(testCase.sarDataJson)
    `Prison API returns Prisoner name for`(testNomisId)
    `Document API upload request is successful for`(subjectAccessRequestId.toString())

    // When
    subjectAccessRequestWorkerService.createSubjectAccessRequestReport(subjectAccessRequest)

    // Then
    `the PDF uploaded to the Document store contains the expected content`(testCase)
    `expected service calls are made`()
  }

  fun `A subject access request for service`(service: String) = SubjectAccessRequest(
    id = subjectAccessRequestId,
    dateFrom = dateFrom,
    dateTo = dateTo,
    sarCaseReferenceNumber = "666",
    services = service,
    nomisId = testNomisId,
    ndeliusCaseReferenceId = ndeliusCaseReferenceNumber,
    requestedBy = "Me",
  )

  fun `Subject Access Request service endpoint returns JSON data`(stubFile: String) {
    val responseBody = getSarResponseStub(stubFile)

    serviceOneMockApi.stubResponseFor(
      ResponseDefinitionBuilder()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(responseBody),
      expectedSubjectAccessRequestParameters,
    )
  }

  fun `Prison API returns Prisoner name for`(nomisId: String) = prisonApi.stubGetOffenderDetails(nomisId)

  fun `Document API upload request is successful for`(subjectAccessRequestId: String) = documentApi.stubUploadFileSuccess(subjectAccessRequestId)

  fun `the PDF uploaded to the Document store contains the expected content`(testCase: TestCase) {
    val expected = getPreGeneratedPdfDocument(testCase.expectedPdf)
    val actual = getUploadedPdfDocument()

    assertThat(actual.numberOfPages).isEqualTo(expected.numberOfPages)

    for (i in 1..actual.numberOfPages) {
      val actualPageN = PdfTextExtractor.getTextFromPage(actual.getPage(i), SimpleTextExtractionStrategy())
      val expectedPageN = PdfTextExtractor.getTextFromPage(expected.getPage(i), SimpleTextExtractionStrategy())

      assertThat(actualPageN).isEqualTo(expectedPageN)
        .withFailMessage("actual page: $i did not match expected")
    }
  }

  fun `expected service calls are made`() {
    hmppsAuth.verifyCalledOnce()
    serviceOneMockApi.verifyApiCalled(1)
    documentApi.verifyStoreDocumentIsCalled(1, subjectAccessRequestId.toString())
  }

  private fun getPreGeneratedPdfDocument(expectedPdfFilename: String): PdfDocument {
    val inputStream = SubjectAccessRequestWorkerServiceIntTest.javaClass
      .getResourceAsStream("$REFERENCE_PDF_BASE_DIR/$expectedPdfFilename")

    assertThat(inputStream).isNotNull

    return pdfDocumentFromInputStream(inputStream!!)
  }

  private fun getUploadedPdfDocument(): PdfDocument = pdfDocumentFromInputStream(ByteArrayInputStream(documentApi.getRequestBodyAsByteArray()))

  private fun pdfDocumentFromInputStream(inputStream: InputStream): PdfDocument = PdfDocument(PdfReader(inputStream))

  private fun getSarResponseStub(filename: String): String = SubjectAccessRequestWorkerServiceIntTest.javaClass
    .getResourceAsStream("$SAR_STUB_RESPONSES_DIR/$filename").use { input ->
      InputStreamReader(input).readText()
    }

  companion object {
    const val REFERENCE_PDF_BASE_DIR = "/integration-tests/reference-pdfs"
    const val SAR_STUB_RESPONSES_DIR = "/integration-tests/api-response-stubs"

    private val subjectAccessRequestId = UUID.fromString("83f1f9af-1036-4273-8252-633f6c7cc1d6")
    private val testNomisId = "nomis-666"
    private val ndeliusCaseReferenceNumber = "ndeliusCaseReferenceId-666"
    private var dateTo = LocalDate.of(2025, 1, 1)
    private var dateFrom = LocalDate.of(2024, 1, 1)

    val expectedSubjectAccessRequestParameters = GetSubjectAccessRequestParams(
      prn = testNomisId,
      crn = ndeliusCaseReferenceNumber,
      dateFrom = dateFrom,
      dateTo = dateTo,
    )

    @JvmStatic
    fun testCases() = listOf(
      TestCase(
        serviceName = "keyworker-api",
        serviceLabel = "Key Worker",
        sarDataJson = "keyworker-api-stub.json",
        expectedPdf = "keyworker-api-reference.pdf",
      ),
      TestCase(
        serviceName = "court-case-service",
        serviceLabel = "Prepare a Case for Sentence",
        sarDataJson = "court-case-service-stub.json",
        expectedPdf = "court-case-service-reference.pdf",
      ),
      TestCase(
        serviceName = "hmpps-offender-categorisation-api",
        serviceLabel = "Categorisation Tool",
        sarDataJson = "hmpps-offender-categorisation-api-stub.json",
        expectedPdf = "hmpps-offender-categorisation-api-reference.pdf",
      ),
      TestCase(
        serviceName = "hmpps-resettlement-passport-api",
        serviceLabel = "Prepare Someone for Release",
        sarDataJson = "hmpps-resettlement-passport-api-stub.json",
        expectedPdf = "hmpps-resettlement-passport-api-reference.pdf",
      ),
    )
  }

  data class TestCase(
    val serviceName: String,
    val serviceLabel: String,
    val sarDataJson: String,
    val expectedPdf: String,
  ) {
    override fun toString() = "SAR request for '$serviceLabel' data generates the expected PDF"
  }
}
