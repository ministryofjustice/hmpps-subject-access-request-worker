package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.layout.Document
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.GotenbergApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.Attachment

@Component
class WordDocumentPdfRenderer(
  private val gotenbergApiClient: GotenbergApiClient,
) : MultiPagePdfRenderer("Word") {

  override fun getPdfBytes(document: Document, attachment: Attachment): ByteArray = gotenbergApiClient
    .convertWordDocToPdf(attachment.data.readBytes(), attachment.info.filename)

  override fun supportedContentTypes(): Set<String> = setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
}
