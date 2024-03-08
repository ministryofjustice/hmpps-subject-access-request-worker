package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Document
import com.itextpdf.text.pdf.PdfPageEventHelper
import com.itextpdf.text.pdf.PdfWriter
import java.io.ByteArrayOutputStream

class PdfService {
  fun getPdfWriter(document: Document, stream: ByteArrayOutputStream): PdfWriter {
    return PdfWriter.getInstance(document, stream)
  }

  fun getCustomHeader(nID: String, sarID: String): CustomHeader {
    return CustomHeader(nID, sarID)
  }

  fun setEvent(writer: PdfWriter, event: PdfPageEventHelper): Int {
    writer.pageEvent = event
    return 0
  }
}
