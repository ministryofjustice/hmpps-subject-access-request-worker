package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import com.itextpdf.layout.element.Paragraph
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.capture
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.secondValue
import org.mockito.kotlin.thirdValue
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_SERVICE_DATA_STATED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_BODY_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_BODY_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_COVER_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_COVER_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_MERGE_SERVICE_PARTIALS_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_MERGE_SERVICE_PARTIALS_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_MERGE_SERVICE_PARTIAL_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_MERGE_SERVICE_PARTIAL_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_SERVICE_DATA_ADDED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RequestServiceDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.SubjectAccessRequestService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments.AttachmentsPdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.createWritablePdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.newDocument
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
  private val subjectAccessRequestService: SubjectAccessRequestService = Mockito.mock()

  @TempDir
  lateinit var sarBaseDir: Path

  @Captor
  lateinit var eventCaptor: ArgumentCaptor<String>

  @Captor
  lateinit var processingEventCaptor: ArgumentCaptor<ProcessingEvent>

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

  private val service1Name = "hmpps-incentives-api"
  private val service1Label = "Incentives"

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
      servicePdfRenderer = ITextServicePdfRenderer(),
      subjectAccessRequestService = subjectAccessRequestService,
    )

    whenever(requestServiceDetail1.serviceConfiguration).thenReturn(service1Config)
    whenever(service1Config.serviceName).thenReturn(service1Name)
    whenever(service1Config.label).thenReturn(service1Label)
    whenever(service1Config.category).thenReturn(ServiceCategory.PRISON)

    whenever(documentStoreService.getTemplateVersion(subjectAccessRequest, service1Name))
      .thenReturn("v1")
    whenever(documentStoreService.listAttachments(subjectAccessRequest, service1Name))
      .thenReturn(emptyList())
  }

  @Nested
  inner class SuccessCases {

    @Test
    fun `should generate expected PDF when not attachment data exists`() = runTest {
      whenever(
        documentStoreService.getDocument(
          subjectAccessRequest = subjectAccessRequest,
          serviceName = service1Name,
          outputPath = sarBaseDir.resolve("html/$service1Name.html"),
        ),
      ).thenReturn(
        getHtmlInputStream(
          path = getResourcePath("/integration-tests/html-stubs/$service1Name-expected.html"),
        ),
      )

      whenever(dateService.reportGenerationDate())
        .thenReturn("1 January 2025")

      whenever(dateService.reportDateFormat(dateFrom, "Start of record"))
        .thenReturn("1 January 2024")

      whenever(dateService.reportDateFormat(dateTo))
        .thenReturn("1 January 2025")

      doNothing().whenever(subjectAccessRequestService)
        .requireSubjectAccessRequestNotCancelled(eq(subjectAccessRequest), any())

      val actualPdfPath = pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest)

      assertThat(actualPdfPath).exists()
      assertThat(actualPdfPath.toFile().length()).isGreaterThan(0L)
      assertThat(actualPdfPath).isEqualTo(sarBaseDir.resolve("report.pdf"))

      verify(telemetryClient, times(12))
        .trackEvent(eventCaptor.capture(), any(), isNull())

      val expectedEvents = listOf(
        GENERATE_PDF_STARTED,
        GENERATE_PDF_BODY_STARTED,
        GENERATE_PDF_COVER_STARTED,
        GENERATE_PDF_COVER_COMPLETED,
        GENERATE_PDF_ADD_SERVICE_DATA_STATED,
        GENERATE_PDF_SERVICE_DATA_ADDED,
        GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED,
        GENERATE_PDF_MERGE_SERVICE_PARTIALS_STARTED,
        GENERATE_PDF_MERGE_SERVICE_PARTIAL_STARTED,
        GENERATE_PDF_MERGE_SERVICE_PARTIAL_COMPLETED,
        GENERATE_PDF_MERGE_SERVICE_PARTIALS_COMPLETED,
        GENERATE_PDF_BODY_COMPLETED,
      ).map { it.name }.toTypedArray()

      assertThat(eventCaptor.allValues).containsExactly(*expectedEvents)

      verify(documentStoreService, times(1))
        .getTemplateVersion(subjectAccessRequest, service1Name)

      verify(documentStoreService, times(1))
        .getDocument(subjectAccessRequest, service1Name, pdfRenderRequest.serviceHtmlPath(service1Config))

      verify(documentStoreService, times(1)).listAttachments(subjectAccessRequest, service1Name)

      verify(attachmentsPdfService, never())
        .processAttachments(any(), any(), any())

      verify(subjectAccessRequestService, times(2)).requireSubjectAccessRequestNotCancelled(
        eq(subjectAccessRequest),
        capture(processingEventCaptor),
      )

      assertThat(processingEventCaptor.allValues).hasSize(2)
      assertThat(processingEventCaptor.firstValue).isEqualTo(GENERATE_PDF_SERVICE_DATA_ADDED)
      assertThat(processingEventCaptor.secondValue).isEqualTo(GENERATE_PDF_MERGE_SERVICE_PARTIAL_STARTED)
    }

    @Test
    fun `should delegate service pdf generation to the configured ServicePdfRenderer`() = runTest {
      val servicePdfRenderer: ServicePdfRenderer = mock()

      val serviceHtml = getHtmlInputStream(
        path = getResourcePath("/integration-tests/html-stubs/$service1Name-expected.html"),
      )

      whenever(
        documentStoreService.getDocument(
          subjectAccessRequest = subjectAccessRequest,
          serviceName = service1Name,
          outputPath = sarBaseDir.resolve("html/$service1Name.html"),
        ),
      ).thenReturn(serviceHtml)

      whenever(dateService.reportGenerationDate())
        .thenReturn("1 January 2025")

      whenever(dateService.reportDateFormat(dateFrom, "Start of record"))
        .thenReturn("1 January 2024")

      whenever(dateService.reportDateFormat(dateTo))
        .thenReturn("1 January 2025")

      doNothing().whenever(subjectAccessRequestService)
        .requireSubjectAccessRequestNotCancelled(eq(subjectAccessRequest), any())

      doAnswer { invocation ->
        val servicePdfPath = invocation.getArgument<Path>(1)
        createWritablePdfDocument(servicePdfPath).use { pdf ->
          newDocument(pdf).use { doc -> doc.add(Paragraph("stub content")) }
        }
      }.whenever(servicePdfRenderer)
        .generateServicePdf(any(), any(), any())

      val pdfServiceWithConfiguredRenderer = PdfService(
        documentStoreService = documentStoreService,
        dateService = dateService,
        attachmentsPdfService = attachmentsPdfService,
        telemetryClient = telemetryClient,
        servicePdfRenderer = servicePdfRenderer,
        subjectAccessRequestService = subjectAccessRequestService,
      )

      val actualPdfPath = pdfServiceWithConfiguredRenderer.renderSubjectAccessRequestPdf(pdfRenderRequest)

      assertThat(actualPdfPath).exists()
      val servicePdfPath = pdfRenderRequest.serviceDataPdfPath(service1Config)

      verify(servicePdfRenderer, times(1))
        .generateServicePdf(
          eq(pdfRenderRequest),
          eq(servicePdfPath),
          eq(serviceHtml),
        )

      verify(subjectAccessRequestService, times(2)).requireSubjectAccessRequestNotCancelled(
        eq(subjectAccessRequest),
        capture(processingEventCaptor),
      )

      assertThat(processingEventCaptor.allValues).hasSize(2)
      assertThat(processingEventCaptor.firstValue).isEqualTo(GENERATE_PDF_SERVICE_DATA_ADDED)
      assertThat(processingEventCaptor.secondValue).isEqualTo(GENERATE_PDF_MERGE_SERVICE_PARTIAL_STARTED)
    }
  }

  @Nested
  inner class RequestCancelled {

    @Test
    fun `should throw exception and not render any service partials when request status cancelled check fails on first call`() = runTest {
      val servicePdfRenderer: ServicePdfRenderer = mock()

      pdfService = PdfService(
        documentStoreService = documentStoreService,
        dateService = dateService,
        attachmentsPdfService = attachmentsPdfService,
        telemetryClient = telemetryClient,
        servicePdfRenderer = servicePdfRenderer,
        subjectAccessRequestService = subjectAccessRequestService,
      )

      val expectedException: FatalSubjectAccessRequestException = mock()

      whenever(requestServiceDetail1.serviceConfiguration).thenReturn(service1Config)
      whenever(service1Config.serviceName).thenReturn("1")
      whenever(service1Config.label).thenReturn("S1")

      doThrow(expectedException).whenever(subjectAccessRequestService)
        .requireSubjectAccessRequestNotCancelled(eq(subjectAccessRequest), any())

      whenever(documentStoreService.getTemplateVersion(subjectAccessRequest, service1Name))
        .thenReturn("v1")

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest)
      }

      assertThat(actual).isEqualTo(expectedException)

      verify(subjectAccessRequestService, times(1)).requireSubjectAccessRequestNotCancelled(
        subjectAccessRequest = eq(subjectAccessRequest),
        event = capture(processingEventCaptor),
      )

      assertThat(processingEventCaptor.allValues).hasSize(1)
      assertThat(processingEventCaptor.firstValue).isEqualTo(GENERATE_PDF_SERVICE_DATA_ADDED)

      verifyNoMoreInteractions(servicePdfRenderer, subjectAccessRequestService)
    }

    @Test
    fun `should throw exception when request status cancelled check fails on 2nd service partial render`() = runTest {
      val servicePdfRenderer: ServicePdfRenderer = mock()
      subjectAccessRequest.services.clear()
      subjectAccessRequest.services.add(requestServiceDetail1)
      subjectAccessRequest.services.add(requestServiceDetail1)

      pdfService = PdfService(
        documentStoreService = documentStoreService,
        dateService = dateService,
        attachmentsPdfService = attachmentsPdfService,
        telemetryClient = telemetryClient,
        servicePdfRenderer = servicePdfRenderer,
        subjectAccessRequestService = subjectAccessRequestService,
      )

      val expectedException: FatalSubjectAccessRequestException = mock()

      doNothing()
        .doThrow(expectedException)
        .whenever(subjectAccessRequestService)
        .requireSubjectAccessRequestNotCancelled(eq(subjectAccessRequest), any())

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest)
      }

      assertThat(actual).isEqualTo(expectedException)

      verify(subjectAccessRequestService, times(2)).requireSubjectAccessRequestNotCancelled(
        eq(subjectAccessRequest),
        capture(processingEventCaptor),
      )

      assertThat(processingEventCaptor.allValues).hasSize(2)
      assertThat(processingEventCaptor.firstValue).isEqualTo(GENERATE_PDF_SERVICE_DATA_ADDED)
      assertThat(processingEventCaptor.secondValue).isEqualTo(GENERATE_PDF_SERVICE_DATA_ADDED)

      verify(servicePdfRenderer, times(1))
        .generateServicePdf(any(), any(), anyOrNull())

      verifyNoMoreInteractions(servicePdfRenderer, subjectAccessRequestService)
    }

    @Test
    fun `should throw exception when request status cancelled check fails during service partial merge phase`() = runTest {
      val servicePdfRenderer: ServicePdfRenderer = mock()
      subjectAccessRequest.services.clear()
      subjectAccessRequest.services.add(requestServiceDetail1)
      subjectAccessRequest.services.add(requestServiceDetail1)

      pdfService = PdfService(
        documentStoreService = documentStoreService,
        dateService = dateService,
        attachmentsPdfService = attachmentsPdfService,
        telemetryClient = telemetryClient,
        servicePdfRenderer = servicePdfRenderer,
        subjectAccessRequestService = subjectAccessRequestService,
      )

      val expectedException: FatalSubjectAccessRequestException = mock()

      doNothing()
        .doNothing()
        .doThrow(expectedException)
        .whenever(subjectAccessRequestService).requireSubjectAccessRequestNotCancelled(
          subjectAccessRequest = eq(subjectAccessRequest),
          event = any(),
        )

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest)
      }

      assertThat(actual).isEqualTo(expectedException)

      verify(subjectAccessRequestService, times(3)).requireSubjectAccessRequestNotCancelled(
        eq(subjectAccessRequest),
        capture(processingEventCaptor),
      )

      assertThat(processingEventCaptor.allValues).hasSize(3)
      assertThat(processingEventCaptor.firstValue).isEqualTo(GENERATE_PDF_SERVICE_DATA_ADDED)
      assertThat(processingEventCaptor.secondValue).isEqualTo(GENERATE_PDF_SERVICE_DATA_ADDED)
      assertThat(processingEventCaptor.thirdValue).isEqualTo(GENERATE_PDF_MERGE_SERVICE_PARTIAL_STARTED)

      verify(servicePdfRenderer, times(2))
        .generateServicePdf(any(), any(), anyOrNull())

      verifyNoMoreInteractions(servicePdfRenderer)
    }
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
