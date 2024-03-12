package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfPageEventHelper
import com.itextpdf.text.pdf.PdfWriter
import org.hibernate.query.sqm.tree.SqmNode.log
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.CustomHeader
import java.io.ByteArrayOutputStream

@Service
class GeneratePdfService {
  fun execute(
    content: Map<String, Any>,
    nID: String,
    sarID: String,
    document: Document = Document(),
    pdfStream: ByteArrayOutputStream = ByteArrayOutputStream(),
  ): ByteArrayOutputStream {
    log.info("Saving report..")
    val writer = getPdfWriter(document, pdfStream)
    val event = getCustomHeader(nID, sarID)
    setEvent(writer, event)
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

  fun addData(document: Document, content: Map<String, Any>) {
    val para = Paragraph()
    val font = FontFactory.getFont(FontFactory.COURIER, 16f, BaseColor.BLACK)
    val boldFont = Font(Font.FontFamily.COURIER, 18f, Font.BOLD)
    content.forEach { entry ->
      log.info(entry.key + entry.value)
      para.add(
        Chunk(
          "${entry.key}\n" + "\n",
          boldFont,
        ),
      )
      if (entry.value is Map<*, *>) {
        (entry.value as Map<*, *>).forEach { value ->
          para.add(
            Chunk(
              "  ${value.key} : ${value.value}\n\n\n",
              font,
            ),
          )
        }
      } else {
        para.add(
          Chunk(
            "  ${entry.value}\n" + "\n" + "\n",
            font,
          ),
        )
      }
    }
    document.add(para)
  }
}
