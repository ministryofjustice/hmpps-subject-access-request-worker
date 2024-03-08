package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream

class PdfService {
  fun getPdfWriter(document: Document, stream: ByteArrayOutputStream): PdfWriter {
    return PdfWriter.getInstance(document, stream)
  }

  fun closePdfWriter(writer: PdfWriter) {
    writer.close()
  }

  fun addRearPage(document: Document, font: Font, numPages: Int) {
    document.newPage()
    val endPageText = Paragraph()
    document.add(Paragraph(300f, "\u00a0"))
    endPageText.alignment = Element.ALIGN_CENTER
    endPageText.add(Chunk("End of Subject Access Request Report\n\n", font))
    endPageText.add(Chunk("Total pages: $numPages", font))
    document.add(endPageText)
  }
}
