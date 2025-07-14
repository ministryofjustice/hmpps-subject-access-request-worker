package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.kernel.geom.PageSize.A4
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.Attachment
import java.io.ByteArrayInputStream

@Component
class PdfDocumentPdfRenderer : AttachmentPdfRenderer {

  override fun add(document: Document, attachment: Attachment) {
    PdfDocument(PdfReader(ByteArrayInputStream(attachment.data.readBytes()))).use {
      document.add(Paragraph("Attachment PDF content follows on subsequent ${it.numberOfPages} page(s)").setTextAlignment(TextAlignment.LEFT))

      val a4Dimensions = Dimensions(A4.width, A4.height)

      for (i in 1..it.numberOfPages) {
        val attachmentPage = it.getPage(i)

        val originalDimensions = Dimensions(attachmentPage.pageSize.width, attachmentPage.pageSize.height)
        val scale = originalDimensions.getScaleToFit(a4Dimensions)
        val position = originalDimensions.applyScale(scale).getPositionToCentreIn(a4Dimensions)

        document.addAttachmentPage(attachmentPage, scale, position)
      }
    }
  }

  override fun supportedContentTypes(): Set<String> = setOf("application/pdf")

  private fun Document.addAttachmentPage(attachmentPage: PdfPage, scale: Float, position: Coordinate) {
    val newPage = this.pdfDocument.addNewPage(A4)
    val canvas = PdfCanvas(newPage)
    canvas.saveState()
    canvas.concatMatrix(scale.toDouble(), 0.0, 0.0, scale.toDouble(), position.x.toDouble(), position.y.toDouble())
    canvas.addXObject(attachmentPage.copyAsFormXObject(this.pdfDocument))
    canvas.restoreState()
    newPage.flush()
  }
}
