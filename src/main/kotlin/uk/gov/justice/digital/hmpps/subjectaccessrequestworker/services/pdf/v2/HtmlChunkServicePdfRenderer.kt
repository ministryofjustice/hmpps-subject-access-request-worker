package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import nu.validator.htmlparser.sax.HtmlParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.HtmlStreamingHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.consumer.PdfHtmlChunkConsumer
import java.io.InputStream
import java.nio.file.Path

class HtmlChunkServicePdfRenderer : ServicePdfRenderer {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(HtmlChunkServicePdfRenderer::class.java)
  }

  override suspend fun generateServicePdf(
    servicePdfPath: Path,
    serviceHtml: InputStream,
  ) {
    try {
      PdfHtmlChunkConsumer(outputPdf = servicePdfPath).use { consumer ->
        val parser = HtmlParser().apply { contentHandler = HtmlStreamingHandler(consumer) }
        parser.parse(InputSource(serviceHtml))
      }
    } catch (e: Exception) {
      log.error("Error while processing html to pdf", e)
      throw e
    }
  }
}