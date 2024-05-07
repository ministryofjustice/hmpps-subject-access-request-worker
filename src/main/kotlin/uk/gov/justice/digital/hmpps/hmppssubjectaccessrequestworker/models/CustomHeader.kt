package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.properties.TextAlignment

class CustomHeader(private val nID: String, private val sarID: String) {
  private var font: PdfFont = PdfFontFactory.createFont(StandardFonts.COURIER)
  fun onEndPage(writer: PdfWriter, document: Document) {
    // val cb = writer.directContent
    // val nIdHeader = Paragraph(nID)
    document.showTextAligned(
      nID,
      36f,
      806f,
      TextAlignment.LEFT,
    )
    // val sarIdHeader = Phrase("CASE REFERENCE: $sarID", font)
    document.showTextAligned(
      "CASE REFERENCE: $sarID",
      36.toFloat(),
      806.toFloat(),
      TextAlignment.RIGHT,
    )
  }
}
