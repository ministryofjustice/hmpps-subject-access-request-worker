package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.CustomHeaderEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments.AttachmentsPdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.createWritablePdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getInputStream
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getReadablePdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.newDocument
import java.io.InputStream
import java.nio.file.Path
import java.time.LocalDate

@Service
class PdfService(
  private val documentStoreService: DocumentStoreService,
  private val dateService: DateService,
  private val attachmentsPdfService: AttachmentsPdfService,
  private val telemetryClient: TelemetryClient,
  private val servicePdfRenderer: ServicePdfRenderer,
) {

  companion object {
    private val log = LoggerFactory.getLogger(PdfService::class.java)
  }

  suspend fun renderSubjectAccessRequestPdf(pdfRenderRequest: PdfRenderRequest): Path {
    telemetryClient.trackSarEvent(GENERATE_PDF_STARTED, pdfRenderRequest.subjectAccessRequest)
    log.info("generating pdf for {}", pdfRenderRequest.subjectAccessRequest.id)

    val reportBodyPageCount = generateReportBody(pdfRenderRequest)
    generateInternalCoverPage(pdfRenderRequest, reportBodyPageCount)
    generateRearPage(pdfRenderRequest, reportBodyPageCount)
    mergePartialsIntoFullReportPdf(pdfRenderRequest)
    return pdfRenderRequest.fullReportPdfPath
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

      val servicePdfPath = pdfRenderRequest.serviceDataPdfPath(serviceConfiguration)
      val serviceHtml = getServiceHtml(pdfRenderRequest, serviceConfiguration)
      log.info("converting service {} html to pdf", subjectAccessRequest.id)
      servicePdfRenderer.generateServicePdf(servicePdfPath, serviceHtml)

      val attachments = documentStoreService.listAttachments(
        subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
        serviceName = serviceConfiguration.serviceName,
      )
      if (!attachments.isEmpty()) {
        val attachmentsPdfPath = pdfRenderRequest.serviceAttachmentsPdfPath(serviceConfiguration)
        createWritablePdfDocument(attachmentsPdfPath).use { attachmentsPdf ->

          newDocument(attachmentsPdf).use { attachmentsDocument ->
            log.info("appending service attachments to {} pdf", subjectAccessRequest.id)

            attachmentsPdfService.processAttachments(
              subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
              serviceName = serviceConfiguration.serviceName,
              document = attachmentsDocument,
            )
          }
        }
      }
      telemetryClient.trackSarEvent(
        event = GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED,
        subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
        "service" to serviceConfiguration.serviceName,
      )
    }
  }

  private suspend fun generateInternalContentsPage(pdfRenderRequest: PdfRenderRequest) {
    telemetryClient.trackSarEvent(GENERATE_PDF_COVER_STARTED, pdfRenderRequest.subjectAccessRequest)

    createWritablePdfDocument(pdfRenderRequest.internalContentsPagePdfPath).use { contentsPagePdf ->
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
    createWritablePdfDocument(pdfRenderRequest.externalCoverPagePdfPath).use { pdfDoc ->
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

  private fun mergeReportBodyPartials(
    pdfRenderRequest: PdfRenderRequest,
  ): Int = createWritablePdfDocument(output = pdfRenderRequest.reportBodyPdfPath).use { reportBodyPdf ->
    val merger = PdfMerger(reportBodyPdf)

    val contentsPagePath = pdfRenderRequest.internalContentsPagePdfPath
    getReadablePdfDocument(getInputStream(contentsPagePath)).use { contentsPage ->
      merger.merge(contentsPage, 1, contentsPage.numberOfPages)
    }

    val externalCoverPagePath = pdfRenderRequest.externalCoverPagePdfPath
    getReadablePdfDocument(getInputStream(externalCoverPagePath)).use { externalCoverPage ->
      merger.merge(externalCoverPage, 1, externalCoverPage.numberOfPages)
    }

    pdfRenderRequest.subjectAccessRequest.getSelectedServices().forEach { service ->
      val pdfPartialPath = pdfRenderRequest.serviceDataPdfPath(service)
      getReadablePdfDocument(getInputStream(pdfPartialPath)).use { servicePartialPdf ->
        merger.merge(servicePartialPdf, 1, servicePartialPdf.numberOfPages)
      }

      val serviceAttachmentsPath = pdfRenderRequest.serviceAttachmentsPdfPath(service)
      if (serviceAttachmentsPath.toFile().exists()) {
        getReadablePdfDocument(getInputStream(serviceAttachmentsPath)).use { serviceAttachmentsPdf ->
          merger.merge(serviceAttachmentsPdf, 1, serviceAttachmentsPdf.numberOfPages)
        }
      }
    }
    reportBodyPdf.numberOfPages
  }

  private fun generateInternalCoverPage(pdfRenderRequest: PdfRenderRequest, reportBodyPageCount: Int) {
    val subjectAccessRequest = pdfRenderRequest.subjectAccessRequest
    val subjectName = pdfRenderRequest.subjectName

    createWritablePdfDocument(pdfRenderRequest.internalCoverPagePdfPath).use { pdfDoc ->
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
        doc.add(
          Paragraph("\nTotal Pages: ${reportBodyPageCount + 2}").setTextAlignment(TextAlignment.CENTER)
            .setFontSize(16f),
        )
        doc.add(Paragraph("\nINTERNAL ONLY").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
        doc.add(Paragraph("\nOFFICIAL-SENSITIVE").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
      }
    }
  }

  private fun generateRearPage(pdfRenderRequest: PdfRenderRequest, reportBodyPageCount: Int) {
    createWritablePdfDocument(output = pdfRenderRequest.rearPagePdfPath).use { pdfDoc ->
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
    createWritablePdfDocument(output = pdfRenderRequest.fullReportPdfPath).use { pdf ->
      newDocument(pdf).use { doc ->
        pdf.addEventHandler(
          PdfDocumentEvent.END_PAGE,
          getCustomerHeaderEventHandler(pdf, doc, pdfRenderRequest),
        )

        val merger = PdfMerger(pdf)

        val internalCoverPagePath = pdfRenderRequest.internalCoverPagePdfPath
        getReadablePdfDocument(getInputStream(internalCoverPagePath)).use { internalCoverPdf ->
          merger.merge(internalCoverPdf, 1, internalCoverPdf.numberOfPages)
        }

        val reportBodyPath = pdfRenderRequest.reportBodyPdfPath
        getReadablePdfDocument(getInputStream(reportBodyPath)).use { reportBodyPdf ->
          merger.merge(reportBodyPdf, 1, reportBodyPdf.numberOfPages)
        }

        val rearPagePath = pdfRenderRequest.rearPagePdfPath
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
    outputPath = pdfRenderRequest.serviceHtmlPath(serviceConfiguration),
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

  private fun List<ServiceConfiguration>.serviceNames() = this.joinToString(",") { it.serviceName }
}
