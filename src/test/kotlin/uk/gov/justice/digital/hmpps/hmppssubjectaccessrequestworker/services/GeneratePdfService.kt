package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.ColumnText
import com.itextpdf.text.pdf.PdfPageEventHelper
import com.itextpdf.text.pdf.PdfWriter

class GeneratePdfService {
}
internal class Header : PdfPageEventHelper() {
  private var font = Font(Font.FontFamily.UNDEFINED, 5f, Font.ITALIC)

  override fun onStartPage(writer: PdfWriter, document: Document) {
    val cb = writer.directContent
    val header = Phrase("this is a header", font)
    ColumnText.showTextAligned(
      cb, Element.ALIGN_CENTER,
      header,
      (document.right() - document.left()) / 2 + document.leftMargin(),
      document.top() + 10, 0f,
    )
  }
}