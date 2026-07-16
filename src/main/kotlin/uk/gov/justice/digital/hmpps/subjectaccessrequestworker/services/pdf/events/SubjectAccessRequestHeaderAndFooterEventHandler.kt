package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.events

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.TextAlignment

open class SubjectAccessRequestHeaderAndFooterEventHandler(
  val document: Document,
  val subjectName: String,
  val nomisId: String?,
  val ndeliusCaseReferenceId: String?,
) : AbstractPdfDocumentEventHandler() {

  private companion object {
    const val BOTTOM_PADDING: Int = 20
    const val FONT_SIZE: Float = 10f
    const val PAGE_FOOTER_TEXT: String = "Official Sensitive"
  }

  override fun onAcceptedEvent(event: AbstractPdfDocumentEvent?) {
    val documentEvent = event as PdfDocumentEvent
    val font: PdfFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val pageSize = documentEvent.page.pageSize
    val leftCoord = pageSize.left + document.leftMargin
    val rightCoord = pageSize.right - document.rightMargin
    val midCoord = (leftCoord + rightCoord) / 2
    val headerY: Float = pageSize.top - document.topMargin
    val footerY: Float = pageSize.bottom + BOTTOM_PADDING
    val canvas = Canvas(documentEvent.page, pageSize)

    canvas.use {
      it.setFont(font)
        .setFontSize(FONT_SIZE)
        .showTextAligned(Paragraph(), leftCoord, headerY, TextAlignment.LEFT) // should this use empty string?
        .simulateBold()
        .showTextAligned(getRightHeaderParagraph(), rightCoord, headerY, TextAlignment.RIGHT)
        .simulateBold()
        .showTextAligned(PAGE_FOOTER_TEXT, midCoord, footerY, TextAlignment.CENTER)
    }
  }

  open fun getRightHeaderParagraph(): Paragraph {
    val subjectIdLabel = nomisId?.let { "NOMIS ID: " } ?: ndeliusCaseReferenceId?.let { "nDelius ID: " } ?: ""
    val subjectIdValue = nomisId ?: ndeliusCaseReferenceId ?: ""

    return Paragraph()
      .add(Text("Name: ").setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)))
      .add(Text(subjectName).setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA)))
      .add(Text("\n"))
      .add(Text(subjectIdLabel).setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)))
      .add(Text(subjectIdValue).setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA)))
  }
}

class SubjectAccessRequestOfficialSensitiveFooterEventHandler(
  document: Document,
) : SubjectAccessRequestHeaderAndFooterEventHandler(
  document = document,
  subjectName = "",
  nomisId = null,
  ndeliusCaseReferenceId = null,
) {
  override fun getRightHeaderParagraph(): Paragraph = Paragraph()
}
