package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import com.microsoft.applicationinsights.TelemetryClient
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RequestServiceDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.AttachmentInfo
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments.AttachmentsPdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments.DefaultPdfRenderer
import java.io.FileInputStream
import java.nio.file.Paths
import java.time.LocalDate
import java.util.UUID

suspend fun main(args: Array<String>) {
  PdfRunner().execute()
}

class PdfRunner {

  private val documentStoreService: DocumentStoreService = mock()
  private val attachmentsPdfService: AttachmentsPdfService = mock()
  private val service1Details: RequestServiceDetail = mock()
  private val service2Details: RequestServiceDetail = mock()
  private val service1Config: ServiceConfiguration = mock()
  private val service2Config: ServiceConfiguration = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val testResourcesDir =
    Paths.get("/Users/david.llewellyn/development/hmpps-subject-access-request-worker/src/test/resources/pdfTest")


  private val subjectAccessRequest = SubjectAccessRequest(
    id = UUID.randomUUID(),
    dateFrom = LocalDate.of(2021, 1, 1),
    dateTo = LocalDate.of(2026, 1, 1),
    sarCaseReferenceNumber = "SAR-101",
    nomisId = "NOM101",
    ndeliusCaseReferenceId = null,
  )

  private val pdfRenderRequest = PdfRenderRequest(
    subjectAccessRequest = subjectAccessRequest,
    subjectName = "Homer Simpson",
    reportDir = Paths.get("/Users/david.llewellyn/development/hmpps-subject-access-request-worker/src/test/resources/pdfTest/output"),
  )

  private val x = AttachmentsPdfService(
    documentStoreService,
    telemetryClient,
    DefaultPdfRenderer(),
    emptyList(),
  )

  private val attachment1 = AttachmentInfo(
    "doc1",
    1,
    "test.txt",
    "text/plain",
    100,
    "test.txt",
  )

  private val pdfService = PdfServiceV2(documentStoreService, DateService(), attachmentsPdfService, telemetryClient)

  suspend fun setUp() {
    subjectAccessRequest.services.addAll(mutableListOf(service1Details, service2Details))

    whenever(documentStoreService.getDocument(any(), eq("service1"), any()))
      .thenReturn(FileInputStream(testResourcesDir.resolve("service1.html").toFile()))

    whenever(documentStoreService.getDocument(any(), eq("service2"), any()))
      .thenReturn(FileInputStream(testResourcesDir.resolve("service2.html").toFile()))

    whenever(documentStoreService.listAttachments(any(), any()))
      .thenReturn(emptyList())
    whenever(documentStoreService.listAttachments(any(), eq("service1")))
      .thenReturn(listOf(attachment1))
      .thenReturn(emptyList())

    doAnswer { invocation ->
      stubAttachmentFunctionality(invocation.arguments[2] as Document)
    }.whenever(attachmentsPdfService).processAttachments(any(), eq("service1"), any())

    whenever(documentStoreService.getTemplateVersion(any(), eq("service1")))
      .thenReturn("v1")
    whenever(documentStoreService.getTemplateVersion(any(), eq("service2")))
      .thenReturn("v31")

    whenever(service1Details.serviceConfiguration).thenReturn(service1Config)
    whenever(service1Config.serviceName).thenReturn("service1")
    whenever(service1Config.label).thenReturn("Service One")
    whenever(service1Config.category).thenReturn(ServiceCategory.PRISON)

    whenever(service2Details.serviceConfiguration).thenReturn(service2Config)
    whenever(service2Config.serviceName).thenReturn("service2")
    whenever(service2Config.label).thenReturn("Service Two")
    whenever(service2Config.category).thenReturn(ServiceCategory.PROBATION)
  }

  suspend fun execute() {
    setUp()
    pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest)
  }

  private fun stubAttachmentFunctionality(document: Document) {
    document.add(
      Paragraph("Attachment: 1").setFontSize(16f)
        .setTextAlignment(TextAlignment.CENTER),
    )
    document.add(Paragraph("homer.png - Homer Simpson").setTextAlignment(TextAlignment.LEFT))
    val imageData =
      ImageDataFactory.create("/Users/david.llewellyn/development/hmpps-subject-access-request-worker/src/test/resources/pdfTest/homer.png")
    val image = Image(imageData)
    image.scaleToFit(500f, 200f)
    document.add(image)
  }
}