package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments.AttachmentsPdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil.Companion.assertPdfContentMatchesExpected
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil.Companion.createPdfDocumentFromBytes
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil.Companion.getPdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil.Companion.getResource
import java.time.LocalDate
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PdfServiceTest {
  private val serviceConfiguration: ServiceConfigurationService = mock()
  private val documentStoreService: DocumentStoreService = mock()
  private val dateService: DateService = mock()
  private val attachmentsPdfService: AttachmentsPdfService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val reportDateFrom = LocalDate.of(2024, 1, 1)
  private val reportDateTo = LocalDate.of(2025, 1, 1)

  private val pdfService = PdfService(
    serviceConfiguration,
    documentStoreService,
    dateService,
    attachmentsPdfService,
    telemetryClient,
  )

  @BeforeEach
  fun setup() {
    /**
     * Dates should match the corresponding date values in the reference PDF.
     */
    whenever(dateService.reportGenerationDate())
      .thenReturn("1 January 2025")

    whenever(dateService.reportDateFormat(any()))
      .thenReturn("1 January 2025")

    whenever(dateService.reportDateFormat(any(), eq("Start of record")))
      .thenReturn("1 January 2024")
  }

  @ParameterizedTest
  @MethodSource("generateReportTestCases")
  fun `should generate the expected PDF`(testCase: TestCase) = runTest {
    val subjectAccessRequest = SubjectAccessRequest(
      id = UUID.fromString("83f1f9af-1036-4273-8252-633f6c7cc1d6"),
      nomisId = "nomis-666",
      ndeliusCaseReferenceId = "ndeliusCaseReferenceId-666",
      sarCaseReferenceNumber = "666",
      dateFrom = reportDateFrom,
      dateTo = reportDateTo,
      services = testCase.serviceName,
    )

    val pdfRenderRequest = PdfService.PdfRenderRequest(
      subjectAccessRequest = subjectAccessRequest,
      subjectName = IntegrationTestFixture.subjectName,
    )

    whenever(documentStoreService.getDocument(subjectAccessRequest, testCase.serviceName))
      .thenReturn(getResource("/integration-tests/html-stubs/${testCase.serviceName}-expected.html"))

    whenever(serviceConfiguration.getSelectedServices(any())).thenReturn(
      listOf(serviceConfiguration(testCase.serviceName, testCase.serviceLabel)),
    )

    val actual = pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest)

    val actualPdf = createPdfDocumentFromBytes(actual)
    val expectedPdf = getPdfDocument("/integration-tests/reference-pdfs/${testCase.serviceName}-reference.pdf")

    assertPdfContentMatchesExpected(actualPdf, expectedPdf)
  }

  private fun serviceConfiguration(serviceName: String, serviceLabel: String) = ServiceConfiguration(
    url = "http://localhost:8080/",
    serviceName = serviceName,
    order = 1,
    label = serviceLabel,
    enabled = true,
  )

  companion object {
    @JvmStatic
    fun generateReportTestCases() = listOf(
      TestCase(
        serviceName = "hmpps-book-secure-move-api",
        serviceLabel = "Book a Secure Move",
      ),
    )

    data class TestCase(val serviceName: String, val serviceLabel: String)
  }
}
