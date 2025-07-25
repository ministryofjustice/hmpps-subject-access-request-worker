package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.layout.Document
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.Attachment

@Component
class PdfDocumentPdfRenderer : MultiPagePdfRenderer("PDF") {

  override fun getPdfBytes(document: Document, attachment: Attachment): ByteArray = attachment.data.readBytes()

  override fun supportedContentTypes(): Set<String> = setOf("application/pdf")
}
