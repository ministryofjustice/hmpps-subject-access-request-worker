package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.events.Event
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.properties.TextAlignment

class CustomHeaderEventHandler(val document: Document, private val nID: String, private val sarID: String) : IEventHandler {

  override fun handleEvent(currentEvent: Event) {
    val docEvent = currentEvent as PdfDocumentEvent
    val font: PdfFont = PdfFontFactory.createFont(StandardFonts.COURIER)
    val pageSize = docEvent.page.pageSize
    val canvas = Canvas(docEvent.page, pageSize)
    canvas
      .setFont(font)
      .setFontSize(5f)
      .showTextAligned(
        nID,
        36f,
        806f,
        TextAlignment.LEFT,
      )
      .showTextAligned(
        "CASE REFERENCE: $sarID",
        36f,
        806f,
        TextAlignment.RIGHT,
      )
      .close()
  }
}
