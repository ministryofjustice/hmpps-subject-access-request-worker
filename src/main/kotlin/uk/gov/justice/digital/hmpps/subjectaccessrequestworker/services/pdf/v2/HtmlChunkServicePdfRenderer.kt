package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import nu.validator.htmlparser.sax.HtmlParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.SubjectAccessRequestHtmlHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.HtmlToPdfConsumer
import java.io.InputStream

class HtmlChunkServicePdfRenderer : ServicePdfRenderer {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(HtmlChunkServicePdfRenderer::class.java)
  }

  override suspend fun generateServicePdf(
    pdfRenderRequest: PdfRenderRequest,
    serviceConfiguration: ServiceConfiguration,
    serviceHtml: InputStream,
  ) {
    try {
      HtmlToPdfConsumer(pdfRenderRequest, serviceConfiguration).use { chunkConsumer ->
        val parser = HtmlParser().apply { contentHandler = SubjectAccessRequestHtmlHandler(chunkConsumer) }
        parser.parse(InputSource(serviceHtml))
      }
    } catch (e: Exception) {
      log.error("Error while processing html to pdf", e)
      throw e
    }
  }
}