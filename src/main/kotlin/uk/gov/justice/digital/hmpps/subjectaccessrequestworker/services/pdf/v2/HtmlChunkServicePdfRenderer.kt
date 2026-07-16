package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import nu.validator.htmlparser.sax.HtmlParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.HtmlStreamingHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.consumer.HtmlToPdfConsumer
import java.io.InputStream
import java.nio.file.Path

class HtmlChunkServicePdfRenderer : ServicePdfRenderer {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(HtmlChunkServicePdfRenderer::class.java)
  }

  override suspend fun generateServicePdf(
    pdfRenderRequest: PdfRenderRequest,
    servicePdfPath: Path,
    serviceHtml: InputStream,
  ) {
    try {
      HtmlToPdfConsumer(pdfRenderRequest, servicePdfPath).use { chunkConsumer ->
        val parser = HtmlParser().apply { contentHandler = HtmlStreamingHandler(chunkConsumer) }
        parser.parse(InputSource(serviceHtml))
      }
    } catch (e: Exception) {
      log.error("Error while processing html to pdf", e)
      throw e
    }
  }
}