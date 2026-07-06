package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.html2pdf.attach.impl.layout.HtmlPageBreak
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.IBlockElement
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.TextAlignment
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_SERVICE_DATA_STATED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_BODY_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_BODY_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_COVER_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_COVER_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_SERVICE_DATA_ADDED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.CustomHeaderEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments.AttachmentsPdfService
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.createTempDirectory

@Service
class PdfServiceV2(
  private val documentStoreService: DocumentStoreService,
  private val dateService: DateService,
  private val attachmentsPdfService: AttachmentsPdfService,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val INTERNAL_COVER_PAGE = "internalCoverPage"
    private const val REPORT_BODY = "reportBody"
    private const val INTERNAL_CONTENTS_PAGE = "internalContentsPage"
    private const val EXTERNAL_COVER_PAGE = "externalCoverPage"
    private const val REAR_PAGE = "rearPage"
    private val converterProperties: ConverterProperties = ConverterProperties()
  }

  suspend fun renderSubjectAccessRequestPdf(pdfRenderRequest: PdfRenderRequest): Path {
    telemetryClient.trackSarEvent(GENERATE_PDF_STARTED, pdfRenderRequest.subjectAccessRequest)
    log.info("generating pdf for {}", pdfRenderRequest.subjectAccessRequest.id)

    val reportBodyPageCount = generateReportBody(pdfRenderRequest)
    generateInternalCoverPage(pdfRenderRequest, reportBodyPageCount)
    generateRearPage(pdfRenderRequest, reportBodyPageCount)
    mergePartialsIntoFullReportPdf(pdfRenderRequest)

    return pdfRenderRequest.getFullPdfPath()
  }

  private suspend fun generateReportBody(pdfRenderRequest: PdfRenderRequest): Int {
    telemetryClient.trackSarEvent(GENERATE_PDF_BODY_STARTED, pdfRenderRequest.subjectAccessRequest)

    generateExternalCoverPage(pdfRenderRequest)
    generateInternalContentsPage(pdfRenderRequest)
    generateServicePartials(pdfRenderRequest)
    val pageCount = mergeReportBodyPartials(pdfRenderRequest)

    telemetryClient.trackSarEvent(GENERATE_PDF_BODY_COMPLETED, pdfRenderRequest.subjectAccessRequest)
    return pageCount
  }

  private suspend fun generateServicePartials(pdfRenderRequest: PdfRenderRequest) {
    val subjectAccessRequest = pdfRenderRequest.subjectAccessRequest
    val services = subjectAccessRequest.getSelectedServices()

    telemetryClient.trackSarEvent(
      event = GENERATE_PDF_ADD_SERVICE_DATA_STATED,
      subjectAccessRequest = subjectAccessRequest,
      "services" to services.serviceNames(),
    )

    services.forEach { serviceConfiguration ->
      telemetryClient.trackSarEvent(
        event = GENERATE_PDF_SERVICE_DATA_ADDED,
        subjectAccessRequest = subjectAccessRequest,
        "service" to serviceConfiguration.serviceName,
      )

      val servicePdfPath = pdfRenderRequest.resolvePartialPdfPath(serviceConfiguration.serviceName)

      createWritablePdfDocument(output = servicePdfPath).use { pdf ->
        newDocument(pdf).use {

          getServiceHtml(pdfRenderRequest, serviceConfiguration).use { htmlInputStream ->
            log.info("converting service {} html to pdf", subjectAccessRequest.id)

            HtmlConverter.convertToElements(htmlInputStream, converterProperties).forEach { element ->
              when (element) {
                is IBlockElement -> it.add(element)
                is Image -> it.add(element)
                is HtmlPageBreak -> it.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                else -> {
                  throw SubjectAccessRequestException("Unsupported element type found ${element.javaClass}")
                }
              }
            }
          }
        }
      }

      val attachments = documentStoreService.listAttachments(
        subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
        serviceName = serviceConfiguration.serviceName,
      )
      if (!attachments.isEmpty()) {
        val attachmentsPdfPath = pdfRenderRequest.resolvePartialPdfPath("${serviceConfiguration.serviceName}-attachments")
        createWritablePdfDocument(attachmentsPdfPath).use { attachmentsPdf ->
          newDocument(attachmentsPdf).use { doc ->
            log.info("appending service attachments to {} pdf", subjectAccessRequest.id)
            attachmentsPdfService.processAttachments(
              subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
              serviceName = serviceConfiguration.serviceName,
              document = doc,
            )
          }
        }
      }
      telemetryClient.trackSarEvent(
        event = GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED,
        subjectAccessRequest =  pdfRenderRequest.subjectAccessRequest,
        "service" to serviceConfiguration.serviceName,
      )
    }
  }

  private suspend fun generateInternalContentsPage(pdfRenderRequest: PdfRenderRequest) {
    telemetryClient.trackSarEvent(GENERATE_PDF_COVER_STARTED, pdfRenderRequest.subjectAccessRequest)

    createWritablePdfDocument(pdfRenderRequest.resolvePartialPdfPath(INTERNAL_CONTENTS_PAGE)).use { contentsPagePdf ->
      newDocument(contentsPagePdf).use { doc ->
        val contentsPageText = Paragraph()
          .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
          .setFontSize(16f)
          .setTextAlignment(TextAlignment.CENTER)

        contentsPageText.add(Text("\n\n\n"))
        contentsPageText.add(Text("CONTENTS\n"))
        doc.add(contentsPageText)

        val services = pdfRenderRequest.subjectAccessRequest.getSelectedServices()
        val serviceListParagraph = Paragraph()
        services.map { getServiceLabelWithTemplateVersion(pdfRenderRequest.subjectAccessRequest, it) }
          .forEach { versionInfo ->
            serviceListParagraph.add(versionInfo)
              .setTextAlignment(TextAlignment.CENTER)
              .setFontSize(14f)
          }

        doc.add(serviceListParagraph)
        doc.add(
          Paragraph("\n\nINTERNAL ONLY")
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(16f),
        )
      }
    }

    telemetryClient.trackSarEvent(GENERATE_PDF_COVER_COMPLETED, pdfRenderRequest.subjectAccessRequest)
  }

  private fun generateExternalCoverPage(pdfRenderRequest: PdfRenderRequest) {
    createWritablePdfDocument(pdfRenderRequest.resolvePartialPdfPath(EXTERNAL_COVER_PAGE)).use { pdfDoc ->
      newDocument(pdfDoc).use { doc ->
        val coverPageText = Paragraph()
          .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
          .setFontSize(16f)
          .setTextAlignment(TextAlignment.CENTER)

        coverPageText.add(Text("\u00a0\n").setFontSize(180f))
        coverPageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
        doc.add(coverPageText)
        doc.add(Paragraph("Name: ${pdfRenderRequest.subjectName}").setTextAlignment(TextAlignment.CENTER))

        val subjectLine = getSubjectIdLine(
          pdfRenderRequest.subjectAccessRequest.nomisId,
          pdfRenderRequest.subjectAccessRequest.ndeliusCaseReferenceId,
        )
        doc.add(Paragraph(subjectLine).setTextAlignment(TextAlignment.CENTER))
        doc.add(
          Paragraph("SAR Case Reference Number: ${pdfRenderRequest.subjectAccessRequest.sarCaseReferenceNumber}")
            .setTextAlignment(TextAlignment.CENTER),
        )
      }
    }
  }

  private fun mergeReportBodyPartials(pdfRenderRequest: PdfRenderRequest): Int {
    return createWritablePdfDocument(output = pdfRenderRequest.resolvePartialPdfPath(REPORT_BODY)).use { reportBodyPdf ->
      val merger = PdfMerger(reportBodyPdf)

      val contentsPagePath = pdfRenderRequest.resolvePartialPdfPath(INTERNAL_CONTENTS_PAGE)
      getReadablePdfDocument(getInputStream(contentsPagePath)).use { contentsPage ->
        merger.merge(contentsPage, 1, contentsPage.numberOfPages)
      }

      val externalCoverPagePath = pdfRenderRequest.resolvePartialPdfPath(EXTERNAL_COVER_PAGE)
      getReadablePdfDocument(getInputStream(externalCoverPagePath)).use { externalCoverPage ->
        merger.merge(externalCoverPage, 1, externalCoverPage.numberOfPages)
      }

      pdfRenderRequest.subjectAccessRequest.getSelectedServices().forEach { service ->
        val pdfPartialPath = pdfRenderRequest.resolvePartialPdfPath(service.serviceName)
        getReadablePdfDocument(getInputStream(pdfPartialPath)).use { servicePartialPdf ->
          merger.merge(servicePartialPdf, 1, servicePartialPdf.numberOfPages)
        }

        val serviceAttachmentsPath = pdfRenderRequest.resolvePartialPdfPath("${service.serviceName}-attachments")
        if (serviceAttachmentsPath.toFile().exists()) {
          getReadablePdfDocument(getInputStream(serviceAttachmentsPath)).use { serviceAttachmentsPdf ->
            merger.merge(serviceAttachmentsPdf, 1, serviceAttachmentsPdf.numberOfPages)
          }
        }
      }
      reportBodyPdf.numberOfPages
    }
  }

  private fun generateInternalCoverPage(pdfRenderRequest: PdfRenderRequest, reportBodyPageCount: Int) {
    val subjectAccessRequest = pdfRenderRequest.subjectAccessRequest
    val subjectName = pdfRenderRequest.subjectName
    val coverPagePath = pdfRenderRequest.resolvePartialPdfPath(INTERNAL_COVER_PAGE)

    createWritablePdfDocument(output = coverPagePath).use { pdfDoc ->
      newDocument(pdfDoc).use { doc ->
        val coverPageText = Paragraph()
          .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
          .setFontSize(16f)
          .setTextAlignment(TextAlignment.CENTER)

        coverPageText.add(Text("\u00a0\n").setFontSize(180f))
        coverPageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
        doc.add(coverPageText).setTextAlignment(TextAlignment.CENTER)

        doc.add(Paragraph("Name: $subjectName")).setTextAlignment(TextAlignment.CENTER)

        val subjectLine = getSubjectIdLine(subjectAccessRequest.nomisId, subjectAccessRequest.ndeliusCaseReferenceId)
        doc.add(Paragraph(subjectLine).setTextAlignment(TextAlignment.CENTER))
        doc.add(
          Paragraph("SAR Case Reference Number: ${subjectAccessRequest.sarCaseReferenceNumber}").setTextAlignment(
            TextAlignment.CENTER,
          ),
        )

        val reportDateRange = getReportDateRangeLine(subjectAccessRequest.dateFrom, subjectAccessRequest.dateTo)
        doc.add(Paragraph(reportDateRange).setTextAlignment(TextAlignment.CENTER))
        doc.add(
          Paragraph("Report generation date: ${dateService.reportGenerationDate()}").setTextAlignment(
            TextAlignment.CENTER,
          ),
        )
        doc.add(Paragraph("\nTotal Pages: ${reportBodyPageCount + 2}").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
        doc.add(Paragraph("\nINTERNAL ONLY").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
        doc.add(Paragraph("\nOFFICIAL-SENSITIVE").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
      }
    }
  }

  private fun generateRearPage(pdfRenderRequest: PdfRenderRequest, reportBodyPageCount: Int) {
    val pdfPath = pdfRenderRequest.resolvePartialPdfPath(REAR_PAGE)
    createWritablePdfDocument(output = pdfPath).use { pdfDoc ->
      newDocument(pdfDoc).use { doc ->
        val endPageText = Paragraph()
          .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
          .setFontSize(16f)
          .setTextAlignment(TextAlignment.CENTER)

        doc.add(Paragraph("\u00a0").setFontSize(300f))

        endPageText.add(Text("End of Subject Access Request Report\n\n"))
        endPageText.add(Text("Total pages: ${reportBodyPageCount + 2}\n\n")) // total = body + cover + rear page
        endPageText.add(Text("INTERNAL ONLY"))
        doc.add(endPageText)
      }
    }
  }

  private fun mergePartialsIntoFullReportPdf(pdfRenderRequest: PdfRenderRequest) {
    createWritablePdfDocument(output = pdfRenderRequest.getFullPdfPath()).use { pdf ->
      newDocument(pdf).use { doc ->
        pdf.addEventHandler(
          PdfDocumentEvent.END_PAGE,
          getCustomerHeaderEventHandler(pdf, doc, pdfRenderRequest),
        )

        val merger = PdfMerger(pdf)

        val internalCoverPagePath = pdfRenderRequest.resolvePartialPdfPath(INTERNAL_COVER_PAGE)
        getReadablePdfDocument(getInputStream(internalCoverPagePath)).use { internalCoverPdf ->
          merger.merge(internalCoverPdf, 1, internalCoverPdf.numberOfPages)
        }

        val reportBodyPath = pdfRenderRequest.resolvePartialPdfPath(REPORT_BODY)
        getReadablePdfDocument(getInputStream(reportBodyPath)).use { reportBodyPdf ->
          merger.merge(reportBodyPdf, 1, reportBodyPdf.numberOfPages)
        }

        val rearPagePath = pdfRenderRequest.resolvePartialPdfPath(REAR_PAGE)
        getReadablePdfDocument(getInputStream(rearPagePath)).use { rearPagePdf ->
          merger.merge(rearPagePdf, 1, rearPagePdf.numberOfPages)
        }
      }
    }
  }

  private suspend fun getServiceHtml(
    pdfRenderRequest: PdfRenderRequest,
    serviceConfiguration: ServiceConfiguration,
  ): InputStream = documentStoreService.getDocument(
    subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
    serviceName = serviceConfiguration.serviceName,
    outputPath = pdfRenderRequest.resolveServiceHtmlPath(serviceConfiguration.serviceName),
  )

  private fun getCustomerHeaderEventHandler(
    pdfDocument: PdfDocument,
    document: Document,
    pdfRenderRequest: PdfRenderRequest,
  ) = CustomHeaderEventHandler(
    pdfDoc = pdfDocument,
    document = document,
    subjectName = pdfRenderRequest.subjectName,
    nomisId = pdfRenderRequest.subjectAccessRequest.nomisId,
    ndeliusCaseReferenceId = pdfRenderRequest.subjectAccessRequest.ndeliusCaseReferenceId,
  )

  private suspend fun getServiceLabelWithTemplateVersion(
    subjectAccessRequest: SubjectAccessRequest,
    serviceConfiguration: ServiceConfiguration,
  ): String {
    val version = documentStoreService.getTemplateVersion(subjectAccessRequest, serviceConfiguration.serviceName)
    return "\u2022 ${serviceConfiguration.label} ($version)\n"
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

  private fun createWritablePdfDocument(output: Path): PdfDocument {
    val pdfDoc = PdfDocument(PdfWriter(FileOutputStream(output.toFile())))
    pdfDoc.isFlushUnusedObjects = true
    return pdfDoc
  }

  private fun getReadablePdfDocument(src: InputStream) = PdfDocument(PdfReader(src))

  private fun getInputStream(path: Path): InputStream = FileInputStream(path.toFile())

  private fun List<ServiceConfiguration>.serviceNames() = this.joinToString(",") { it.serviceName }

  private fun newDocument(
    pdf: PdfDocument,
  ): Document = Document(pdf, pdf.defaultPageSize, true).apply {
    setMargins(50F, 35F, 70F, 35F)
  }
}
