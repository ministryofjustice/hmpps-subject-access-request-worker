package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_SERVICE_DATA_STATED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_BODY_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_BODY_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_COVER_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_COVER_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_SERVICE_DATA_ADDED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RequestServiceDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments.AttachmentsPdfService
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID
import kotlin.io.path.toPath

@ExtendWith(MockitoExtension::class)
class PdfServiceTest {
  private val documentStoreService: DocumentStoreService = Mockito.mock()
  private val dateService: DateService = Mockito.mock()
  private val attachmentsPdfService: AttachmentsPdfService = Mockito.mock()
  private val service1Config: ServiceConfiguration = Mockito.mock()
  private val telemetryClient: TelemetryClient = Mockito.mock()
  private val requestServiceDetail1: RequestServiceDetail = Mockito.mock()

  @TempDir
  lateinit var sarBaseDir: Path

  @Captor
  lateinit var eventCaptor: ArgumentCaptor<String>

  private lateinit var pdfService: PdfService
  private lateinit var pdfRenderRequest: PdfRenderRequest

  private val dateFrom = LocalDate.of(2024, 1, 1)
  private val dateTo = LocalDate.of(2025, 1, 1)

  private val subjectAccessRequest = SubjectAccessRequest(
    id = UUID.randomUUID(),
    dateFrom = dateFrom,
    dateTo = dateTo,
    sarCaseReferenceNumber = "666",
    nomisId = "nomis-666",
    ndeliusCaseReferenceId = null,
  )

  private val serviceName = "hmpps-incentives-api"
  private val serviceLabel = "Incentives"

  @BeforeEach
  fun setup() = runTest {
    pdfRenderRequest = PdfRenderRequest(
      subjectAccessRequest = subjectAccessRequest,
      subjectName = "REACHER, Joe",
      reportDir = sarBaseDir,
    )

    subjectAccessRequest.services.add(requestServiceDetail1)

    pdfService = PdfService(
      documentStoreService = documentStoreService,
      dateService = dateService,
      attachmentsPdfService = attachmentsPdfService,
      telemetryClient = telemetryClient,
    )
  }

  @Test
  fun `should generate expected PDF when not attachment data exists`() = runTest {
    whenever(requestServiceDetail1.serviceConfiguration)
      .thenReturn(service1Config)

    whenever(service1Config.serviceName)
      .thenReturn(serviceName)

    whenever(service1Config.label)
      .thenReturn(serviceLabel)

    whenever(documentStoreService.getTemplateVersion(subjectAccessRequest, serviceName))
      .thenReturn("v1")

    whenever(
      documentStoreService.getDocument(
        subjectAccessRequest = subjectAccessRequest,
        serviceName = serviceName,
        outputPath = sarBaseDir.resolve("html/$serviceName.html"),
      ),
    ).thenReturn(
      getHtmlInputStream(
        path = getResourcePath("/integration-tests/html-stubs/$serviceName-expected.html"),
      ),
    )

    whenever(documentStoreService.listAttachments(subjectAccessRequest, serviceName))
      .thenReturn(emptyList())

    whenever(dateService.reportGenerationDate())
      .thenReturn("1 January 2025")

    whenever(dateService.reportDateFormat(dateFrom, "Start of record"))
      .thenReturn("1 January 2024")

    whenever(dateService.reportDateFormat(dateTo))
      .thenReturn("1 January 2025")

    val actualPdfPath = pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest)

    assertThat(actualPdfPath).exists()
    assertThat(actualPdfPath.toFile().length()).isGreaterThan(0L)
    assertThat(actualPdfPath).isEqualTo(sarBaseDir.resolve("report.pdf"))

    verify(telemetryClient, times(8))
      .trackEvent(eventCaptor.capture(), any(), isNull())

    assertThat(eventCaptor.allValues).containsExactly(
      GENERATE_PDF_STARTED.toString(),
      GENERATE_PDF_BODY_STARTED.toString(),
      GENERATE_PDF_COVER_STARTED.toString(),
      GENERATE_PDF_COVER_COMPLETED.toString(),
      GENERATE_PDF_ADD_SERVICE_DATA_STATED.toString(),
      GENERATE_PDF_SERVICE_DATA_ADDED.toString(),
      GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED.toString(),
      GENERATE_PDF_BODY_COMPLETED.toString(),
    )

    verify(documentStoreService, times(1))
      .getTemplateVersion(subjectAccessRequest, serviceName)

    verify(documentStoreService, times(1))
      .getDocument(subjectAccessRequest, serviceName, pdfRenderRequest.serviceHtmlPath(service1Config))

    verify(documentStoreService, times(1)).listAttachments(subjectAccessRequest, serviceName)

    verify(attachmentsPdfService, never())
      .processAttachments(any(), any(), any())
  }

  private fun assertPageMatchesExpected(actualPdfDoc: PdfDocument, expectedPdfDoc: PdfDocument, pageNumber: Int) {
    val expected = actualPdfDoc.getPage(pageNumber)
    val actual = expectedPdfDoc.getPage(pageNumber)

    val actualPageText = PdfTextExtractor.getTextFromPage(actual, SimpleTextExtractionStrategy())
    val expectedPageText = PdfTextExtractor.getTextFromPage(expected, SimpleTextExtractionStrategy())

    assertThat(actualPageText).isEqualTo(expectedPageText)
  }

  private fun getHtmlInputStream(path: Path): InputStream = FileInputStream(path.toFile())

  private fun getResourcePath(filepath: String): Path {
    val absolutePath = this::class.java.getResource(filepath)
      ?.toURI()
      ?.toPath()
      ?: fail("failed to get resource for specified path")
    return absolutePath
  }
}
