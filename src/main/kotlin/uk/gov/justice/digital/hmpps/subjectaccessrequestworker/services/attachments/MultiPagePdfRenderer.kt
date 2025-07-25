package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.attachments

import com.itextpdf.kernel.geom.PageSize.A4
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.Attachment
import java.io.ByteArrayInputStream

abstract class MultiPagePdfRenderer(
  private val type: String,
) : AttachmentPdfRenderer {

  override fun add(
    document: Document,
    attachment: Attachment,
  ) {
    val pdfBytes = getPdfBytes(document, attachment)
    addToDoc(pdfBytes, document)
  }

  abstract fun getPdfBytes(document: Document, attachment: Attachment): ByteArray

  private fun addToDoc(pdfBytes: ByteArray, document: Document) {
    val reader = PdfReader(ByteArrayInputStream(pdfBytes))
    PdfDocument(reader).use { pdf ->
      document.add(Paragraph("Attachment $type content follows on subsequent ${pdf.numberOfPages} page(s)").setTextAlignment(TextAlignment.LEFT))

      val a4Dimensions = Dimensions(A4.width, A4.height)

      for (i in 1..pdf.numberOfPages) {
        val attachmentPage = pdf.getPage(i)
        val originalDimensions = Dimensions(attachmentPage.pageSize.width, attachmentPage.pageSize.height)
        val scale = originalDimensions.getScaleToFit(a4Dimensions)
        val position = originalDimensions.applyScale(scale).getPositionToCentreIn(a4Dimensions)

        document.addAttachmentPage(attachmentPage, scale, position)
      }
    }
  }

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
