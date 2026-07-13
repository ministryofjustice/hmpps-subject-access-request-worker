package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking

import nu.validator.htmlparser.sax.HtmlParser
import org.xml.sax.InputSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.consumer.HtmlChunkFileWriterConsumer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.consumer.HtmlChunkPdfConsumer
import java.io.FileInputStream
import java.nio.file.Paths

// Replace paths/files as desired
val BASE_DIR = Paths.get("/Users/david.llewellyn/development/hmpps-subject-access-request-worker/src/test/resources/pdfTest")
val HTML_INTPUT = BASE_DIR.resolve("hmpps-support-additional-needs-api-example.html")
val PDF_OUTPUT = BASE_DIR.resolve("output/chunked.pdf")
val HTML_OUTPUT = BASE_DIR.resolve("output/chunked.html")

fun main(args: Array<String>) {

  HtmlChunkFileWriterConsumer(HTML_OUTPUT).use { consumer ->
    val styleSheet = HtmlStyleSheetExtractor(HTML_INTPUT).getStyleSheet()

    // Use the HTML parser to send chunks of HTML to the PDF consumer to iteratively build the PDF document
    HtmlChunkPdfConsumer(PDF_OUTPUT).use { consumer ->
      val parser = HtmlParser().apply { contentHandler = HtmlStreamingHandler(consumer, styleSheet) }
      parser.parse(InputSource(FileInputStream(HTML_INTPUT.toFile())))
    }
  }
}