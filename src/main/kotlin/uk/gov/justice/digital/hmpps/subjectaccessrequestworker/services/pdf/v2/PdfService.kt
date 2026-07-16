package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.kernel.utils.PdfMerger
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments.AttachmentsPdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.createWritablePdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.events.SubjectAccessRequestHeaderAndFooterEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.events.SubjectAccessRequestOfficialSensitiveFooterEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getInputStream
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getReadablePdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.memoryUsage
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

    log.info("generateInternalCoverPage: {}", memoryUsage())
    generateInternalCoverPage(pdfRenderRequest, reportBodyPageCount)

    log.info("generateRearPage: {}", memoryUsage())
    generateRearPage(pdfRenderRequest, reportBodyPageCount)

    log.info("mergePartialsIntoFullReportPdf: {}", memoryUsage())
    mergePartialsIntoFullReportPdf(pdfRenderRequest)

    log.info("complete: {}", memoryUsage())
    return pdfRenderRequest.fullReportPdfPath
  }

  private suspend fun generateReportBody(pdfRenderRequest: PdfRenderRequest): Int {
    telemetryClient.trackSarEvent(GENERATE_PDF_BODY_STARTED, pdfRenderRequest.subjectAccessRequest)

    generateExternalCoverPage(pdfRenderRequest)
    generateInternalContentsPage(pdfRenderRequest)
    generateServicePartials(pdfRenderRequest)

    log.info("generateServicePartials completed: {}", memoryUsage())
    val pageCount = mergeReportBodyPartials(pdfRenderRequest)
    log.info("mergeReportBodyPartials completed: {}", memoryUsage())

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
      log.info("converting service {} html to pdf using {}", subjectAccessRequest.id, servicePdfRenderer::class.simpleName)
      servicePdfRenderer.generateServicePdf(pdfRenderRequest, servicePdfPath, serviceHtml)

      generateServiceAttachments(pdfRenderRequest, serviceConfiguration)

      telemetryClient.trackSarEvent(
        event = GENERATE_PDF_ADD_SERVICE_DATA_COMPLETED,
        subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
        "service" to serviceConfiguration.serviceName,
      )
    }
  }

  private suspend fun generateServiceAttachments(
    pdfRenderRequest: PdfRenderRequest,
    serviceConfiguration: ServiceConfiguration,
  ) {
    val subjectAccessRequest = pdfRenderRequest.subjectAccessRequest

    documentStoreService.listAttachments(
      subjectAccessRequest = subjectAccessRequest,
      serviceName = serviceConfiguration.serviceName,
    ).takeIf { it.isNotEmpty() }?.let {
      val attachmentsPdfPath = pdfRenderRequest.serviceAttachmentsPdfPath(serviceConfiguration)

      createWritablePdfDocument(attachmentsPdfPath).use { pdf ->
        newDocument(pdf).use { attachmentsDocument ->
          log.info("appending service attachments to {} pdf", subjectAccessRequest.id)

          pdf.addEventHandler(
            PdfDocumentEvent.END_PAGE,
            SubjectAccessRequestHeaderAndFooterEventHandler(
              document = attachmentsDocument,
              subjectName = pdfRenderRequest.subjectName,
              nomisId = subjectAccessRequest.nomisId,
              ndeliusCaseReferenceId = subjectAccessRequest.ndeliusCaseReferenceId,
            ),
          )

          attachmentsPdfService.processAttachments(
            subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
            serviceName = serviceConfiguration.serviceName,
            document = attachmentsDocument,
          )
        }
      }
    }
  }

  private suspend fun generateInternalContentsPage(pdfRenderRequest: PdfRenderRequest) {
    telemetryClient.trackSarEvent(GENERATE_PDF_COVER_STARTED, pdfRenderRequest.subjectAccessRequest)

    createWritablePdfDocument(pdfRenderRequest.internalContentsPagePdfPath).use { pdf ->
      newDocument(pdf).use { document ->
        pdf.addEventHandler(
          PdfDocumentEvent.END_PAGE,
          SubjectAccessRequestOfficialSensitiveFooterEventHandler(document),
        )

        val contentsPageText = Paragraph()
          .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
          .setFontSize(16f)
          .setTextAlignment(TextAlignment.CENTER)

        contentsPageText.add(Text("\n\n\n"))
        contentsPageText.add(Text("CONTENTS\n"))
        document.add(contentsPageText)

        val services = pdfRenderRequest.subjectAccessRequest.getSelectedServices()
        val serviceListParagraph = Paragraph()
        services.map { getServiceLabelWithTemplateVersion(pdfRenderRequest.subjectAccessRequest, it) }
          .forEach { versionInfo ->
            serviceListParagraph.add(versionInfo)
              .setTextAlignment(TextAlignment.CENTER)
              .setFontSize(14f)
          }

        document.add(serviceListParagraph)
        document.add(
          Paragraph("\n\nINTERNAL ONLY")
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(16f),
        )
      }
    }

    telemetryClient.trackSarEvent(GENERATE_PDF_COVER_COMPLETED, pdfRenderRequest.subjectAccessRequest)
  }

  private fun generateExternalCoverPage(pdfRenderRequest: PdfRenderRequest) {
    createWritablePdfDocument(pdfRenderRequest.externalCoverPagePdfPath).use { pdf ->
      newDocument(pdf).use { document ->
        pdf.addEventHandler(
          PdfDocumentEvent.END_PAGE,
          SubjectAccessRequestOfficialSensitiveFooterEventHandler(document),
        )

        val coverPageText = Paragraph()
          .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
          .setFontSize(16f)
          .setTextAlignment(TextAlignment.CENTER)

        coverPageText.add(Text("\u00a0\n").setFontSize(180f))
        coverPageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
        document.add(coverPageText)
        document.add(Paragraph("Name: ${pdfRenderRequest.subjectName}").setTextAlignment(TextAlignment.CENTER))

        val subjectLine = getSubjectIdLine(
          pdfRenderRequest.subjectAccessRequest.nomisId,
          pdfRenderRequest.subjectAccessRequest.ndeliusCaseReferenceId,
        )
        document.add(Paragraph(subjectLine).setTextAlignment(TextAlignment.CENTER))
        document.add(
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

    createWritablePdfDocument(pdfRenderRequest.internalCoverPagePdfPath).use { pdf ->
      newDocument(pdf).use { document ->
        pdf.addEventHandler(
          PdfDocumentEvent.END_PAGE,
          SubjectAccessRequestOfficialSensitiveFooterEventHandler(document),
        )

        val coverPageText = Paragraph()
          .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
          .setFontSize(16f)
          .setTextAlignment(TextAlignment.CENTER)

        coverPageText.add(Text("\u00a0\n").setFontSize(180f))
        coverPageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
        document.add(coverPageText).setTextAlignment(TextAlignment.CENTER)

        document.add(Paragraph("Name: $subjectName")).setTextAlignment(TextAlignment.CENTER)

        val subjectLine = getSubjectIdLine(subjectAccessRequest.nomisId, subjectAccessRequest.ndeliusCaseReferenceId)
        document.add(Paragraph(subjectLine).setTextAlignment(TextAlignment.CENTER))
        document.add(
          Paragraph("SAR Case Reference Number: ${subjectAccessRequest.sarCaseReferenceNumber}").setTextAlignment(
            TextAlignment.CENTER,
          ),
        )

        val reportDateRange = getReportDateRangeLine(subjectAccessRequest.dateFrom, subjectAccessRequest.dateTo)
        document.add(Paragraph(reportDateRange).setTextAlignment(TextAlignment.CENTER))
        document.add(
          Paragraph("Report generation date: ${dateService.reportGenerationDate()}").setTextAlignment(
            TextAlignment.CENTER,
          ),
        )
        document.add(
          Paragraph("\nTotal Pages: ${reportBodyPageCount + 2}").setTextAlignment(TextAlignment.CENTER)
            .setFontSize(16f),
        )
        document.add(Paragraph("\nINTERNAL ONLY").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
        document.add(Paragraph("\nOFFICIAL-SENSITIVE").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
      }
    }
  }

  private fun generateRearPage(pdfRenderRequest: PdfRenderRequest, reportBodyPageCount: Int) {
    createWritablePdfDocument(output = pdfRenderRequest.rearPagePdfPath).use { pdf ->
      newDocument(pdf).use { document ->
        pdf.addEventHandler(
          PdfDocumentEvent.END_PAGE,
          SubjectAccessRequestOfficialSensitiveFooterEventHandler(document),
        )

        val endPageText = Paragraph()
          .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
          .setFontSize(16f)
          .setTextAlignment(TextAlignment.CENTER)

        document.add(Paragraph("\u00a0").setFontSize(300f))

        endPageText.add(Text("End of Subject Access Request Report\n\n"))
        endPageText.add(Text("Total pages: ${reportBodyPageCount + 2}\n\n")) // total = body + cover + rear page
        endPageText.add(Text("INTERNAL ONLY"))
        document.add(endPageText)
      }
    }
  }

  private fun mergePartialsIntoFullReportPdf(pdfRenderRequest: PdfRenderRequest) {
    createWritablePdfDocument(output = pdfRenderRequest.fullReportPdfPath).use { pdf ->
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

  private suspend fun getServiceHtml(
    pdfRenderRequest: PdfRenderRequest,
    serviceConfiguration: ServiceConfiguration,
  ): InputStream = documentStoreService.getDocument(
    subjectAccessRequest = pdfRenderRequest.subjectAccessRequest,
    serviceName = serviceConfiguration.serviceName,
    outputPath = pdfRenderRequest.serviceHtmlPath(serviceConfiguration),
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
