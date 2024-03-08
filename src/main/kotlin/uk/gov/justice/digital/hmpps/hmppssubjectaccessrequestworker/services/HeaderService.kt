package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.ColumnText
import com.itextpdf.text.pdf.PdfPageEventHelper
import com.itextpdf.text.pdf.PdfWriter

class CustomHeader(private val nID: String, private val sarID: String) : PdfPageEventHelper() {
  private var font = Font(Font.FontFamily.COURIER, 10f)

  override fun onEndPage(writer: PdfWriter, document: Document) {
    val cb = writer.directContent
    val nIdHeader = Phrase(nID, font)
    ColumnText.showTextAligned(
      cb, Element.ALIGN_LEFT,
      nIdHeader,
      document.leftMargin(),
      document.top() + 40, 0f,
    )
    val sarIdHeader = Phrase("CASE REFERENCE: $sarID", font)
    ColumnText.showTextAligned(
      cb, Element.ALIGN_RIGHT,
      sarIdHeader,
      document.right(),
      document.top() + 40, 0f,
    )
  }
}