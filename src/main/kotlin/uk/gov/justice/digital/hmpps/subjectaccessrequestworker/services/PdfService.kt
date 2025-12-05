package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.IBlockElement
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.TextAlignment
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_SERVICE_DATA_STATED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_BODY_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_BODY_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_COVER_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_COVER_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_SERVICE_DATA_ADDED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.CustomHeaderEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments.AttachmentsPdfService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate

@Service
class PdfService(
  private val serviceConfigurationService: ServiceConfigurationService,
  private val documentStoreService: DocumentStoreService,
  private val dateService: DateService,
  private val attachmentsPdfService: AttachmentsPdfService,
  private val telemetryClient: TelemetryClient,
) {

  data class PdfRenderRequest(
    val subjectAccessRequest: SubjectAccessRequest,
    val subjectName: String,
  )

  suspend fun renderSubjectAccessRequestPdf(pdfRenderRequest: PdfRenderRequest): ByteArrayOutputStream {
    telemetryClient.trackSarEvent(GENERATE_PDF_STARTED, pdfRenderRequest.subjectAccessRequest)
    val bodyOutputStream = ByteArrayOutputStream()
    val bodyWrapper = createPdfDocument(bodyOutputStream).use { pdfDocument ->
      telemetryClient.trackSarEvent(GENERATE_PDF_BODY_STARTED, pdfRenderRequest.subjectAccessRequest)

      createDocumentBodyPdf(pdfRenderRequest, pdfDocument)
      PdfOutputStreamWrapper(bodyOutputStream, pdfDocument.numberOfPages).also {
        telemetryClient.trackSarEvent(GENERATE_PDF_BODY_COMPLETED, pdfRenderRequest.subjectAccessRequest)
      }
    }

    val coverOutputStream = ByteArrayOutputStream()
    val coverWrapper = createPdfDocument(coverOutputStream).use { pdfDocument ->
      telemetryClient.trackSarEvent(GENERATE_PDF_COVER_STARTED, pdfRenderRequest.subjectAccessRequest)
      createSubjectAccessRequestDocument(pdfDocument).addInternalCoverPage(
        pdfRenderRequest.subjectName,
        pdfRenderRequest.subjectAccessRequest,
        bodyWrapper.numberOfPages - 1,
      )
      PdfOutputStreamWrapper(coverOutputStream, pdfDocument.numberOfPages).also {
        telemetryClient.trackSarEvent(GENERATE_PDF_COVER_COMPLETED, pdfRenderRequest.subjectAccessRequest)
      }
    }

    return mergeBodyAndCoverDocuments(bodyWrapper, coverWrapper).also {
      telemetryClient.trackSarEvent(GENERATE_PDF_COMPLETED, pdfRenderRequest.subjectAccessRequest)
    }
  }

  private fun createPdfDocument(outputStream: OutputStream): PdfDocument = PdfDocument(PdfWriter(outputStream))

  /**
   * Merge the cover and body into a single document.
   */
  private fun mergeBodyAndCoverDocuments(
    body: PdfOutputStreamWrapper,
    cover: PdfOutputStreamWrapper,
  ): ByteArrayOutputStream {
    try {
      val fullDocumentOutputStream = ByteArrayOutputStream()

      createPdfDocument(fullDocumentOutputStream).use { fullPdfDocument ->
        val merger = PdfMerger(fullPdfDocument)

        PdfDocument(PdfReader(cover.toInputStream())).use { coverDoc ->
          merger.merge(coverDoc, 1, 1)
        }

        PdfDocument(PdfReader(body.toInputStream())).use { bodyDoc ->
          merger.merge(bodyDoc, 1, (bodyDoc.numberOfPages))
        }
      }
      return fullDocumentOutputStream
    } finally {
      body.outputStream.close()
      cover.outputStream.close()
    }
  }

  private fun fontHelvetica(): PdfFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)

  private suspend fun createDocumentBodyPdf(
    pdfRenderRequest: PdfRenderRequest,
    pdfDocument: PdfDocument,
  ) {
    val document = createSubjectAccessRequestDocument(pdfDocument)
    pdfDocument.addSubjectAccessRequestCustomHandler(document, pdfRenderRequest)

    val services = serviceConfigurationService.getSelectedServices(pdfRenderRequest.subjectAccessRequest)
    document.addInternalContentsPage(pdfRenderRequest.subjectAccessRequest, services)
    document.addExternalCoverPage(pdfRenderRequest)
    document.addServiceData(pdfRenderRequest.subjectAccessRequest, services)
    document.addRearPage(pdfDocument.numberOfPages)
  }

  private suspend fun Document.addInternalContentsPage(
    subjectAccessRequest: SubjectAccessRequest,
    services: List<ServiceConfiguration>,
  ) {
    val contentsPageText = Paragraph()
      .setFont(fontHelvetica())
      .setFontSize(16f)
      .setTextAlignment(TextAlignment.CENTER)

    contentsPageText.add(Text("\n\n\n"))
    contentsPageText.add(Text("CONTENTS\n"))
    this.add(contentsPageText)

    val serviceListParagraph = Paragraph()
    services.map { getServiceLabelWithTemplateVersion(subjectAccessRequest, it) }.forEach {
      serviceListParagraph.add(it)
        .setTextAlignment(TextAlignment.CENTER)
        .setFontSize(14f)
    }

    this.add(serviceListParagraph)
    this.add(
      Paragraph("\n\nINTERNAL ONLY")
        .setTextAlignment(TextAlignment.CENTER)
        .setFontSize(16f),
    )
  }

  private suspend fun getServiceLabelWithTemplateVersion(
    subjectAccessRequest: SubjectAccessRequest,
    service: ServiceConfiguration,
  ): String {
    val version = documentStoreService.getTemplateVersion(subjectAccessRequest, service.serviceName)
    return "\u2022 ${service.label} ($version)\n"
  }

  private fun Document.addExternalCoverPage(pdfRenderRequest: PdfRenderRequest) {
    this.add(AreaBreak(AreaBreakType.NEXT_PAGE))
    val coverPageText = Paragraph()
      .setFont(fontHelvetica())
      .setFontSize(16f)
      .setTextAlignment(TextAlignment.CENTER)

    coverPageText.add(Text("\u00a0\n").setFontSize(180f))
    coverPageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
    this.add(coverPageText)
    this.add(Paragraph("Name: ${pdfRenderRequest.subjectName}").setTextAlignment(TextAlignment.CENTER))

    val subjectLine = getSubjectIdLine(
      pdfRenderRequest.subjectAccessRequest.nomisId,
      pdfRenderRequest.subjectAccessRequest.ndeliusCaseReferenceId,
    )
    this.add(Paragraph(subjectLine).setTextAlignment(TextAlignment.CENTER))
    this.add(
      Paragraph("SAR Case Reference Number: ${pdfRenderRequest.subjectAccessRequest.sarCaseReferenceNumber}")
        .setTextAlignment(TextAlignment.CENTER),
    )
  }

  private suspend fun Document.addServiceData(
    subjectAccessRequest: SubjectAccessRequest,
    services: List<ServiceConfiguration>,
  ) {
    telemetryClient.trackSarEvent(
      event = GENERATE_PDF_ADD_SERVICE_DATA_STATED,
      subjectAccessRequest = subjectAccessRequest,
      "services" to services.serviceNames(),
    )
    services.forEach { service ->
      telemetryClient.trackSarEvent(
        event = GENERATE_PDF_SERVICE_DATA_ADDED,
        subjectAccessRequest = subjectAccessRequest,
        "service" to service.serviceName,
      )
      this.add(AreaBreak(AreaBreakType.NEXT_PAGE))

      val elements = documentStoreService.getDocument(
        subjectAccessRequest = subjectAccessRequest,
        serviceName = service.serviceName,
      ).use { HtmlConverter.convertToElements(it) }

      elements.forEach { this.add(it as IBlockElement) }
      telemetryClient.trackSarEvent(
        event = GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED,
        subjectAccessRequest = subjectAccessRequest,
        "service" to service.serviceName,
      )

      attachmentsPdfService.processAttachments(subjectAccessRequest, service.serviceName, this)
    }
  }

  private fun Document.addRearPage(numPages: Int) {
    this.add(AreaBreak(AreaBreakType.NEXT_PAGE))

    val endPageText = Paragraph()
      .setFont(fontHelvetica())
      .setFontSize(16f)
      .setTextAlignment(TextAlignment.CENTER)

    this.add(Paragraph("\u00a0").setFontSize(300f))

    endPageText.add(Text("End of Subject Access Request Report\n\n"))
    endPageText.add(Text("Total pages: ${numPages + 2}\n\n"))
    endPageText.add(Text("INTERNAL ONLY"))
    this.add(endPageText)
  }

  private fun PdfDocument.addSubjectAccessRequestCustomHandler(
    document: Document,
    pdfRenderRequest: PdfRenderRequest,
  ) {
    this.addEventHandler(
      PdfDocumentEvent.END_PAGE,
      CustomHeaderEventHandler(
        this,
        document,
        getSubjectIdLine(
          pdfRenderRequest.subjectAccessRequest.nomisId,
          pdfRenderRequest.subjectAccessRequest.ndeliusCaseReferenceId,
        ),
        pdfRenderRequest.subjectName,
      ),
    )
  }

  private fun Document.addInternalCoverPage(
    subjectName: String,
    subjectAccessRequest: SubjectAccessRequest,
    numPages: Int,
  ) {
    val coverPageText = Paragraph()
      .setFont(fontHelvetica())
      .setFontSize(16f)
      .setTextAlignment(TextAlignment.CENTER)

    coverPageText.add(Text("\u00a0\n").setFontSize(180f))
    coverPageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
    this.add(coverPageText).setTextAlignment(TextAlignment.CENTER)

    this.add(Paragraph("Name: $subjectName")).setTextAlignment(TextAlignment.CENTER)

    val subjectLine = getSubjectIdLine(subjectAccessRequest.nomisId, subjectAccessRequest.ndeliusCaseReferenceId)
    this.add(Paragraph(subjectLine).setTextAlignment(TextAlignment.CENTER))
    this.add(
      Paragraph("SAR Case Reference Number: ${subjectAccessRequest.sarCaseReferenceNumber}").setTextAlignment(
        TextAlignment.CENTER,
      ),
    )

    val reportDateRange = getReportDateRangeLine(subjectAccessRequest.dateFrom, subjectAccessRequest.dateTo)
    this.add(Paragraph(reportDateRange).setTextAlignment(TextAlignment.CENTER))
    this.add(
      Paragraph("Report generation date: ${dateService.reportGenerationDate()}").setTextAlignment(
        TextAlignment.CENTER,
      ),
    )
    this.add(Paragraph("\nTotal Pages: ${numPages + 2}").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
    this.add(Paragraph("\nINTERNAL ONLY").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
    this.add(Paragraph("\nOFFICIAL-SENSITIVE").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
  }

  private fun createSubjectAccessRequestDocument(pdfDocument: PdfDocument): Document {
    val doc = Document(pdfDocument)
    doc.setMargins(50F, 35F, 70F, 35F)
    return doc
  }

  private fun getSubjectIdLine(nomisId: String?, ndeliusCaseReferenceId: String?): String {
    var subjectIdLine = ""

    if (StringUtils.isNotEmpty(nomisId)) {
      subjectIdLine = "NOMIS ID: $nomisId"
    } else if (StringUtils.isNotEmpty(ndeliusCaseReferenceId)) {
      subjectIdLine = "nDelius ID: $ndeliusCaseReferenceId"
    }
    return subjectIdLine
  }

  private fun getReportDateRangeLine(dateFrom: LocalDate?, dateTo: LocalDate?): String {
    val formattedDateTo = dateService.reportDateFormat(dateTo!!)
    val formattedDateFrom: String = dateService.reportDateFormat(dateFrom, "Start of record")
    return "Report date range: $formattedDateFrom - $formattedDateTo"
  }

  private data class PdfOutputStreamWrapper(val outputStream: ByteArrayOutputStream, val numberOfPages: Int) {
    fun toInputStream(): ByteArrayInputStream = ByteArrayInputStream(outputStream.toByteArray())
  }

  private fun List<ServiceConfiguration>.serviceNames() = this.joinToString(",") { it.serviceName }
}
