package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.consumer

import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.html2pdf.attach.impl.layout.HtmlPageBreak
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.IBlockElement
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.properties.AreaBreakType
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import java.io.FileOutputStream
import java.nio.file.Path

class HtmlChunkPdfConsumer(val outputPdf: Path) : HtmlChunkConsumer {

  private companion object {
    private val log = LoggerFactory.getLogger(HtmlChunkPdfConsumer::class.java)
  }

  private val pdfDocument = PdfDocument(PdfWriter(FileOutputStream(outputPdf.toFile())))
  private val document = Document(pdfDocument).apply {
    setMargins(50F, 35F, 70F, 35F)
  }

  override fun consume(chunk: String) {
    HtmlConverter.convertToElements(chunk).forEach { element ->
      when (element) {
        is IBlockElement -> document.add(element)
        is Image -> document.add(element)
        is HtmlPageBreak -> document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        else -> {
          throw SubjectAccessRequestException("Unsupported element type found ${element.javaClass}")
        }
      }
    }

    document.flush()

    val runtime = Runtime.getRuntime()
    log.info("Heap used MB={}", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024)
  }

  override fun close() {
    log.info("closing PDF document: {}", outputPdf.toUri())
    this.pdfDocument.close()
  }
}
