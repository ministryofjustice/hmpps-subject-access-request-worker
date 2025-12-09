package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.io.font.constants.StandardFonts.HELVETICA
import com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory.createFont
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.TextAlignment

class CustomHeaderEventHandler(private val pdfDoc: PdfDocument, val document: Document, private val subjectName: String, private val nomisId: String?, private val ndeliusCaseReferenceId: String?) : AbstractPdfDocumentEventHandler() {

  override fun onAcceptedEvent(currentEvent: AbstractPdfDocumentEvent) {
    val docEvent = currentEvent as PdfDocumentEvent
    val leftHeaderText: String
    val rightHeaderText: Paragraph
    if (pdfDoc.getPageNumber(docEvent.page) <= 2) {
      leftHeaderText = ""
      rightHeaderText = Paragraph()
    } else {
      val subjectIdLabel = nomisId?.let { "NOMIS ID: " } ?: ndeliusCaseReferenceId?.let { "nDelius ID: " } ?: ""
      val subjectIdValue = nomisId ?: ndeliusCaseReferenceId ?: ""
      leftHeaderText = ""
      rightHeaderText = Paragraph()
        .add(Text("Name: ").setFont(createFont(HELVETICA_BOLD)))
        .add(Text(subjectName).setFont(createFont(HELVETICA)))
        .add(Text("\n"))
        .add(Text(subjectIdLabel).setFont(createFont(HELVETICA_BOLD)))
        .add(Text(subjectIdValue).setFont(createFont(HELVETICA)))
    }
    val font: PdfFont = createFont(StandardFonts.HELVETICA)
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
