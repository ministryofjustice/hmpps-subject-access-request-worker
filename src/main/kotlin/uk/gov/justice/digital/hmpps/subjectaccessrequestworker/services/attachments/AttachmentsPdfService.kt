package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.TextAlignment
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService

@Service
@ConditionalOnProperty(name = ["html-renderer.enabled"], havingValue = "true")
class AttachmentsPdfService(
  private val documentStoreService: DocumentStoreService,
  private val telemetryClient: TelemetryClient,
  private val defaultPdfRenderer: DefaultPdfRenderer,
  renderers: List<AttachmentPdfRenderer>,
) {
  private val rendererMap: Map<String, AttachmentPdfRenderer> =
    renderers.flatMap { renderer -> renderer.supportedContentTypes().map { it to renderer } }.toMap()

  suspend fun processAttachments(subjectAccessRequest: SubjectAccessRequest, serviceName: String, document: Document) {
    telemetryClient.trackSarEvent("pdfAddAttachmentsStarted", subjectAccessRequest, "service" to serviceName)
    documentStoreService.listAttachments(subjectAccessRequest, serviceName).forEach { attachmentInfo ->
      telemetryClient.trackSarEvent("pdfAddAttachmentStarted", subjectAccessRequest, "service" to serviceName, "attachmentNumber" to "${attachmentInfo.attachmentNumber}", "filename" to attachmentInfo.filename)

      val attachment = documentStoreService.getAttachment(subjectAccessRequest, serviceName, attachmentInfo)
      document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
      document.add(Paragraph("Attachment: ${attachment.info.attachmentNumber}").setFontSize(16f).setTextAlignment(TextAlignment.CENTER))
      document.add(Paragraph("${attachment.info.filename} - ${attachment.info.name}").setTextAlignment(TextAlignment.LEFT))
      val pdfRenderer = rendererMap[attachment.info.contentType] ?: defaultPdfRenderer
      pdfRenderer.add(document, attachment)

      telemetryClient.trackSarEvent("pdfAddAttachmentCompleted", subjectAccessRequest, "service" to serviceName, "attachmentNumber" to "${attachmentInfo.attachmentNumber}", "filename" to attachmentInfo.filename)
    }
    telemetryClient.trackSarEvent("pdfAddAttachmentsCompleted", subjectAccessRequest, "service" to serviceName)
  }
}
