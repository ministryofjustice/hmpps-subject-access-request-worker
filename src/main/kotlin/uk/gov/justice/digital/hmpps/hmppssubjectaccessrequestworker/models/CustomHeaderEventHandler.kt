package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.events.Event
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.properties.TextAlignment

class CustomHeaderEventHandler(private val pdfDoc: PdfDocument, val document: Document, private val nID: String, private val sarID: String) : IEventHandler {

  override fun handleEvent(currentEvent: Event) {
    val docEvent = currentEvent as PdfDocumentEvent
    if (pdfDoc.getPageNumber(docEvent.page) <= 2) {
      return
    }
    val font: PdfFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val pageSize = docEvent.page.pageSize
    val leftCoord = pageSize.left + document.leftMargin
    val rightCoord = pageSize.right - document.rightMargin
    val headerY: Float = pageSize.top - document.topMargin + 10
    val canvas = Canvas(docEvent.page, pageSize)
    canvas
      .setFont(font)
      .setFontSize(10f)
      .showTextAligned(
        nID,
        leftCoord,
        headerY,
        TextAlignment.LEFT,
      )
      .showTextAligned(
        "CASE REFERENCE: $sarID",
        rightCoord,
        headerY,
        TextAlignment.RIGHT,
      )
      .close()
  }
}
