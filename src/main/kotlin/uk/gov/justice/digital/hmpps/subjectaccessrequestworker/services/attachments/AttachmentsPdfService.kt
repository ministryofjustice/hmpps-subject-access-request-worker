package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.TextAlignment
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENTS_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENTS_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENT_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENT_STARTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService

@Service
class AttachmentsPdfService(
  private val documentStoreService: DocumentStoreService,
  private val telemetryClient: TelemetryClient,
  private val defaultPdfRenderer: DefaultPdfRenderer,
  renderers: List<AttachmentPdfRenderer>,
) {
  private val rendererMap: Map<String, AttachmentPdfRenderer> =
    renderers.flatMap { renderer -> renderer.supportedContentTypes().map { it to renderer } }.toMap()

  suspend fun processAttachments(subjectAccessRequest: SubjectAccessRequest, serviceName: String, document: Document) {
    telemetryClient.trackSarEvent(
      event = GENERATE_PDF_ADD_ATTACHMENTS_STARTED,
      subjectAccessRequest = subjectAccessRequest,
      "service" to serviceName,
    )
    val attachmentInfos = documentStoreService.listAttachments(subjectAccessRequest, serviceName)

    attachmentInfos.takeIf { it.isNotEmpty() }
      ?.let {
        it.forEachIndexed { index, attachmentInfo ->
          telemetryClient.trackSarEvent(
            event = GENERATE_PDF_ADD_ATTACHMENT_STARTED,
            subjectAccessRequest = subjectAccessRequest,
            "service" to serviceName,
            "attachmentNumber" to "${attachmentInfo.attachmentNumber}",
            "filename" to attachmentInfo.filename,
          )

          // Attachments are added to a new Document so the first attachment will automatically start on a new page.
          // Add a page break after the first attachment to ensure subsequent attachment starts on a new page.
          if (index > 0) document.add(AreaBreak(AreaBreakType.NEXT_PAGE))

          val attachment = documentStoreService.getAttachment(subjectAccessRequest, serviceName, attachmentInfo)
          document.add(
            Paragraph("Attachment: ${attachment.info.attachmentNumber}").setFontSize(16f)
              .setTextAlignment(TextAlignment.CENTER),
          )
          document.add(Paragraph("${attachment.info.filename} - ${attachment.info.name}").setTextAlignment(TextAlignment.LEFT))
          val pdfRenderer = rendererMap[attachment.info.contentType] ?: defaultPdfRenderer
          pdfRenderer.add(document, attachment)

          telemetryClient.trackSarEvent(
            event = GENERATE_PDF_ADD_ATTACHMENT_COMPLETED,
            subjectAccessRequest = subjectAccessRequest,
            "service" to serviceName,
            "attachmentNumber" to "${attachmentInfo.attachmentNumber}",
            "filename" to attachmentInfo.filename,
          )
        }
        telemetryClient.trackSarEvent(
          event = GENERATE_PDF_ADD_ATTACHMENTS_COMPLETED,
          subjectAccessRequest = subjectAccessRequest,
          "service" to serviceName,
        )
      } ?: telemetryClient.trackSarEvent(
      event = ProcessingEvent.GENERATE_PDF_ADD_ATTACHMENTS_EMPTY,
      subjectAccessRequest = subjectAccessRequest,
      "service" to serviceName,
    )
  }
}
