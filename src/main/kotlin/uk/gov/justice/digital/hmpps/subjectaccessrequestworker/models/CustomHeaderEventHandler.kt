package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.properties.TextAlignment

class CustomHeaderEventHandler(private val pdfDoc: PdfDocument, val document: Document, private val subjectIdLine: String, private val subjectName: String) : AbstractPdfDocumentEventHandler() {

  override fun onAcceptedEvent(currentEvent: AbstractPdfDocumentEvent) {
    val docEvent = currentEvent as PdfDocumentEvent
    val leftHeaderText: String
    val rightHeaderText: String
    if (pdfDoc.getPageNumber(docEvent.page) <= 2) {
      leftHeaderText = ""
      rightHeaderText = ""
    } else {
      leftHeaderText = ""
      rightHeaderText = """
          |Name: $subjectName
          |$subjectIdLine
      """.trimMargin()
    }
    val font: PdfFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val pageSize = docEvent.page.pageSize
    val leftCoord = pageSize.left + document.leftMargin
    val rightCoord = pageSize.right - document.rightMargin
    val midCoord = (leftCoord + rightCoord) / 2
    val headerY: Float = pageSize.top - document.topMargin
    val footerY: Float = pageSize.bottom + 20
    val canvas = Canvas(docEvent.page, pageSize)
    canvas
      .setFont(font)
      .setFontSize(10f)
      .showTextAligned(
        leftHeaderText,
        leftCoord,
        headerY,
        TextAlignment.LEFT,
      ).simulateBold()
      .showTextAligned(
        rightHeaderText,
        rightCoord,
        headerY,
        TextAlignment.RIGHT,
      ).simulateBold()
      .showTextAligned(
        "Official Sensitive",
        midCoord,
        footerY,
        TextAlignment.CENTER,
      )
      .close()
  }
}
