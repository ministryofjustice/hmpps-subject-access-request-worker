package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.layout.Document
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.Attachment

interface AttachmentPdfRenderer {
  fun add(document: Document, attachment: Attachment)
  fun supportedContentTypes(): Set<String>
}
