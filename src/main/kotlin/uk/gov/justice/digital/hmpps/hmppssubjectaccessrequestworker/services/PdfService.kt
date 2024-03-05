package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Document
import com.itextpdf.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream

class PdfService {
  fun getPdfWriter(document: Document, stream: ByteArrayOutputStream): Int {
    PdfWriter.getInstance(document, stream)
    return 0
  }
}
