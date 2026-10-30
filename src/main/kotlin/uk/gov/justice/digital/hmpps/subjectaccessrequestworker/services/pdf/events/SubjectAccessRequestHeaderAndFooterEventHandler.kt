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
  val pageMargins: PageMargins,
  val subjectName: String,
  val nomisId: String?,
  val ndeliusCaseReferenceId: String?,
) : AbstractPdfDocumentEventHandler() {

  private companion object {
    const val BOTTOM_PADDING: Int = 20
    const val FONT_SIZE: Float = 10f
    const val PAGE_FOOTER_TEXT: String = "Official Sensitive"
  }

  private val helveticaFont: PdfFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)
  private val helveticaBoldFont: PdfFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)

  private val subjectIdLabel = nomisId?.let { "NOMIS ID: " } ?: ndeliusCaseReferenceId?.let { "nDelius ID: " } ?: ""
  private val subjectIdValue = nomisId ?: ndeliusCaseReferenceId ?: ""

  private val rightHeaderParagraph = Paragraph()
    .add(Text("Name: ").setFont(helveticaBoldFont))
    .add(Text(subjectName).setFont(helveticaFont))
    .add(Text("\n"))
    .add(Text(subjectIdLabel).setFont(helveticaBoldFont))
    .add(Text(subjectIdValue).setFont(helveticaFont))


  override fun onAcceptedEvent(event: AbstractPdfDocumentEvent?) {
    val documentEvent = event as PdfDocumentEvent
    val pageSize = documentEvent.page.pageSize
    val leftCoord = pageSize.left + pageMargins.left
    val rightCoord = pageSize.right - pageMargins.right
    val midCoord = (leftCoord + rightCoord) / 2
    val headerY: Float = pageSize.top - pageMargins.top
    val footerY: Float = pageSize.bottom + BOTTOM_PADDING
    val canvas = Canvas(documentEvent.page, pageSize)

    canvas.use {
      it.setFont(helveticaFont)
        .setFontSize(FONT_SIZE)
        .showTextAligned(Paragraph(), leftCoord, headerY, TextAlignment.LEFT) // should this use empty string?
        .simulateBold()
        .showTextAligned(getRightHeaderParagraph(), rightCoord, headerY, TextAlignment.RIGHT)
        .simulateBold()
        .showTextAligned(PAGE_FOOTER_TEXT, midCoord, footerY, TextAlignment.CENTER)
    }
  }

  open fun getRightHeaderParagraph(): Paragraph  = rightHeaderParagraph
}

class SubjectAccessRequestOfficialSensitiveFooterEventHandler(
  pageMargins: PageMargins,
) : SubjectAccessRequestHeaderAndFooterEventHandler(
  pageMargins,
  subjectName = "",
  nomisId = null,
  ndeliusCaseReferenceId = null,
) {
  override fun getRightHeaderParagraph(): Paragraph = Paragraph()
}

class PageMargins(val left: Float, val right: Float, val top: Float)
