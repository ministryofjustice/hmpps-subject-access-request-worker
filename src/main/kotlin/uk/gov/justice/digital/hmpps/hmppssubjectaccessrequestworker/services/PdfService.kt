package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream


class PdfService {
  fun getPdfWriter(document: Document, stream: ByteArrayOutputStream): PdfWriter {
    return PdfWriter.getInstance(document, stream)
  }

  fun getPdfReader(stream: ByteArrayOutputStream): PdfReader{
    return PdfReader(stream.toByteArray())
  }

  fun closePdfWriter(writer: PdfWriter) {
    writer.close()
  }
  fun addRearPage(document: Document, stream: ByteArrayOutputStream, font: Font) {
    document.newPage()
    val reader = getPdfReader(stream)
    val numPages = reader.numberOfPages
    val endPageText = Paragraph()
    endPageText.alignment = Element.ALIGN_CENTER
    endPageText.add(Chunk("End of Subject Access Request Report", font))
    endPageText.add(Chunk("Total pages: $numPages", font))
    document.add(endPageText)
  }
}
