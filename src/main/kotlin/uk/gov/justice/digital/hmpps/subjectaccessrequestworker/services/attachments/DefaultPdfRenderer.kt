package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.Attachment

@Component
class DefaultPdfRenderer : AttachmentPdfRenderer {

  override fun add(document: Document, attachment: Attachment) {
    document.add(Paragraph("Attachment content type ${attachment.info.contentType} not supported").setTextAlignment(TextAlignment.LEFT))
  }

  override fun supportedContentTypes(): Set<String> = emptySet()
}
