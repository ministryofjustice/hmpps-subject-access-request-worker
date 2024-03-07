package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
import org.hibernate.query.sqm.tree.SqmNode.log
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class GeneratePdfService {
  fun execute(
    content: Map<String, Any>,
    document: Document = Document(),
    pdfStream: ByteArrayOutputStream = ByteArrayOutputStream(),
    pdfService: PdfService = PdfService(),
  ): ByteArrayOutputStream {
    log.info("Saving report..")
    pdfService.getPdfWriter(document, pdfStream)
    document.open()
    log.info("Started writing to PDF")
    val font: Font = FontFactory.getFont(FontFactory.COURIER, 16f, BaseColor.BLACK)
    log.info("Set font")
    if (content == emptyMap<Any, Any>()) {
      document.add(Chunk("NO DATA FOUND", font))
    }
    content.forEach { entry ->
      log.info(entry.key + entry.value)
      document.add(Chunk("${entry.key} : ${entry.value}", font))
    }
    log.info("Finished writing report")
    document.close()
    log.info("PDF complete")
    return pdfStream
  }
}
