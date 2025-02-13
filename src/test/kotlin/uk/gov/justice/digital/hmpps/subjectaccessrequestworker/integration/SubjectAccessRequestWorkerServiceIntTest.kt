package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.GetSubjectAccessRequestParams
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceOneApiExtension.Companion.serviceOneMockApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.SubjectAccessRequestWorkerService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils.TemplateTestingUtil
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

  @MockitoBean
  private var dateService: DateService = mock()

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling the documentStore API.
    oAuth2AuthorizedClientService.removeAuthorizedClient("sar-client", "anonymousUser")

    prisonDetailsRepository.saveAndFlush(PrisonDetail("MDI", "MOORLAND (HMP & YOI)"))
    prisonDetailsRepository.saveAndFlush(PrisonDetail("LEI", "LEEDS (HMP)"))

    hmppsAuth.stubGrantToken()
  }

  /**
   * Generates a SAR report from static data then compare the content of the output PDF document to a pre-generated
   * reference PDF file for the requested service - see: src/test/resources/integration-tests/reference-pdfs.
   *
   * NOTE: If the template/response data changes you may need to generate an updated reference pdf for affected service.
   * See the [uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils.TemplateTestingUtil] readme
   * for details.
   */
  @ParameterizedTest
  @MethodSource("generateReportTestCases")
  fun `SAR worker generates and uploads the expected PDF to the document store`(testCase: TestCase) {
    /**
     * Ensure test generated reports have the same 'report generation date' as pre-generated reference reports.
     */
    whenever(dateService.now()).thenReturn(TemplateTestingUtil.reportGenerationDate)

    // Given
    val subjectAccessRequest = newSubjectAccessRequestFor(service = testCase.serviceName)
    `Subject Access Request service endpoint returns JSON data`(testCase.dataJsonFile)
    `Prison API returns Prisoner name for`(testNomisId)
    `Document API upload request is successful for`(subjectAccessRequest.id.toString())

    // When
    subjectAccessRequestWorkerService.createSubjectAccessRequestReport(subjectAccessRequest)

    // Then
    `the PDF uploaded to the Document store contains the expected content`(testCase)
    `expected service calls are made`()
  }

  @Test
  fun `should throw exception if requested service does not exist`() {
    val serviceName = "this-service-does-not-exist"
    val subjectAccessRequest = newSubjectAccessRequestFor(service = serviceName)

    val actual = assertThrows<FatalSubjectAccessRequestException> {
      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(subjectAccessRequest)
    }

    assertThat(actual.event).isEqualTo(ProcessingEvent.GET_SERVICE_CONFIGURATION)
    assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)
    assertThat(actual.params).containsExactlyEntriesOf(mapOf("serviceName" to serviceName))
    assertThat(actual.message).contains("service with name '$serviceName' not found")

    documentApi.verifyNeverCalled()
    serviceOneMockApi.verifyNeverCalled()
    prisonApi.verifyNeverCalled()
  }

  @Test
  fun `should throw exception if request to service API is unsuccessful`() {
    val subjectAccessRequest = newSubjectAccessRequestFor(service = "keyworker-api")

    serviceOneMockApi.stubSubjectAccessRequestErrorResponse(
      status = 500,
      params = GetSubjectAccessRequestParams(
        prn = subjectAccessRequest.nomisId,
        crn = subjectAccessRequest.ndeliusCaseReferenceId,
        dateFrom = subjectAccessRequest.dateFrom,
        dateTo = subjectAccessRequest.dateTo,
      ),
    )

    val actual = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(subjectAccessRequest)
    }

    assertThat(actual.event).isEqualTo(ProcessingEvent.GET_SAR_DATA)
    assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)
    assertThat(actual.params).containsExactlyEntriesOf(mapOf("uri" to "http://localhost:4100"))
    assertThat(actual.message).contains("subjectAccessRequest failed and max retry attempts (2) exhausted")
    assertThat(actual.cause).isNotNull()
    assertThat(actual.cause!!.message).contains("500 Internal Server Error from GET http://localhost:${serviceOneMockApi.port()}/subject-access-request")

    serviceOneMockApi.verifyApiCalled(3)
    documentApi.verifyNeverCalled()
    prisonApi.verifyNeverCalled()
  }

  @Test
  fun `should throw exception if document api request is unsuccessful`() {
    val subjectAccessRequest = newSubjectAccessRequestFor(service = "keyworker-api")
    whenever(dateService.now()).thenReturn(LocalDate.now())

    serviceOneMockApi.stubResponseFor(
      ResponseDefinitionBuilder()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(getSarResponseStub("keyworker-api-stub.json")),
      expectedSubjectAccessRequestParameters,
    )

    documentApi.stubUploadFileFailsWithStatus(subjectAccessRequest.id.toString(), 500)

    val actual = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(subjectAccessRequest)
    }

    assertThat(actual.event).isEqualTo(ProcessingEvent.STORE_DOCUMENT)
    assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)
    assertThat(actual.params).containsExactlyEntriesOf(mapOf("uri" to "/documents/SUBJECT_ACCESS_REQUEST_REPORT/${subjectAccessRequest.id}"))
    assertThat(actual.message).contains("subjectAccessRequest failed and max retry attempts (2) exhausted")
    assertThat(actual.cause).isNotNull()
    assertThat(actual.cause!!.message).contains("500 Internal Server Error from POST http://localhost:${documentApi.port()}/documents/SUBJECT_ACCESS_REQUEST_REPORT/${subjectAccessRequest.id}")

    serviceOneMockApi.verifyApiCalled(1)
    documentApi.verifyStoreDocumentIsCalled(3, subjectAccessRequest.id.toString())
    prisonApi.verifyApiCalled(1, subjectAccessRequest.nomisId!!)
  }

  fun newSubjectAccessRequestFor(service: String) = SubjectAccessRequest(
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

  fun `Document API upload request is successful for`(subjectAccessRequestId: String) = documentApi
    .stubUploadFileSuccess(subjectAccessRequestId)

  fun `the PDF uploaded to the Document store contains the expected content`(testCase: TestCase) {
    val expected = getPreGeneratedPdfDocument(testCase.referencePdf)
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

  private fun getUploadedPdfDocument(): PdfDocument = pdfDocumentFromInputStream(
    ByteArrayInputStream(documentApi.getRequestBodyAsByteArray()),
  )

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
    fun generateReportTestCases() = listOf(
      TestCase(
        serviceName = "keyworker-api",
        serviceLabel = "Key Worker",
      ),
      TestCase(
        serviceName = "offender-case-notes",
        serviceLabel = "Sensitive Case Notes",
      ),
      TestCase(
        serviceName = "court-case-service",
        serviceLabel = "Prepare a Case for Sentence",
      ),
      TestCase(
        serviceName = "hmpps-restricted-patients-api",
        serviceLabel = "Restricted Patients",
      ),
      TestCase(
        serviceName = "hmpps-accredited-programmes-api",
        serviceLabel = "Accredited Programmes",
      ),
      TestCase(
        serviceName = "hmpps-complexity-of-need",
        serviceLabel = "Complexity Of Need",
      ),
      TestCase(
        serviceName = "offender-management-allocation-manager",
        serviceLabel = "Manage Prison Offender Manager Cases",
      ),
      TestCase(
        serviceName = "hmpps-book-secure-move-api",
        serviceLabel = "Book a Secure Move",
      ),
      TestCase(
        serviceName = "hmpps-non-associations-api",
        serviceLabel = "Non-associations",
      ),
      TestCase(
        serviceName = "hmpps-incentives-api",
        serviceLabel = "Incentives",
      ),
      TestCase(
        serviceName = "hmpps-manage-adjudications-api",
        serviceLabel = "Manage Adjudications",
      ),
      TestCase(
        serviceName = "hmpps-offender-categorisation-api",
        serviceLabel = "Categorisation Tool",
      ),
      TestCase(
        serviceName = "hmpps-hdc-api",
        serviceLabel = "Home Detention Curfew",
      ),
      TestCase(
        serviceName = "create-and-vary-a-licence-api",
        serviceLabel = "Create and Vary a Licence",
      ),
      TestCase(
        serviceName = "hmpps-uof-data-api",
        serviceLabel = "Use of Force",
      ),
      TestCase(
        serviceName = "hmpps-activities-management-api",
        serviceLabel = "Manage Activities and Appointments",
      ),
      TestCase(
        serviceName = "hmpps-resettlement-passport-api",
        serviceLabel = "Prepare Someone for Release",
      ),
      TestCase(
        serviceName = "hmpps-approved-premises-api",
        serviceLabel = "Approved Premises",
      ),
      TestCase(
        serviceName = "hmpps-education-employment-api",
        serviceLabel = "Education Employment",
      ),
    )
  }

  data class TestCase(
    val serviceName: String,
    val serviceLabel: String,
    val dataJsonFile: String = "$serviceName-stub.json",
    val referencePdf: String = "$serviceName-reference.pdf",
  ) {
    override fun toString() = "SAR request for '$serviceLabel' data generates the expected PDF"
  }
}
