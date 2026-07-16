package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.HtmlChunkServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.ITextServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.OpenHtmlServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.ServicePdfRenderer

@Configuration
class RenderConfig {

  private companion object {
    private val log = LoggerFactory.getLogger(RenderConfig::class.java)
  }

  @Bean
  fun servicePdfRenderer(
    applicationProperties: ApplicationProperties,
  ): ServicePdfRenderer = when (applicationProperties.serviceRenderer) {
    ServiceRenderer.ITEXT -> ITextServicePdfRenderer()
    ServiceRenderer.OPENHTMLTOPDF -> OpenHtmlServicePdfRenderer()
    ServiceRenderer.HTML_CHUNKER -> HtmlChunkServicePdfRenderer()
  }.also { log.info("configured rendering service pdf: {}", it::class.java.simpleName) }
}

enum class ServiceRenderer {
  ITEXT,
  OPENHTMLTOPDF,
  HTML_CHUNKER,
}
