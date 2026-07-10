package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.ITextServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.OpenHtmlServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.ServicePdfRenderer

@Configuration
class RenderConfig {

  @Bean
  fun servicePdfRenderer(applicationProperties: ApplicationProperties): ServicePdfRenderer = when (applicationProperties.serviceRenderer) {
    ServiceRenderer.ITEXT -> ITextServicePdfRenderer()
    ServiceRenderer.OPENHTMLTOPDF -> OpenHtmlServicePdfRenderer()
  }
}

enum class ServiceRenderer {
  ITEXT,
  OPENHTMLTOPDF,
}
