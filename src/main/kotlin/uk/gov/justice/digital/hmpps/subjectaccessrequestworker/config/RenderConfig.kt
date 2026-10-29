package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.TempDirectoryService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.ITextServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.ItextIncrementalServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.OpenHtmlServicePdfRenderer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.ServicePdfRenderer

@Configuration
class RenderConfig(
  private val telemetryClient: TelemetryClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Bean
  fun servicePdfRenderer(
    applicationProperties: ApplicationProperties,
    tempDirectoryService: TempDirectoryService,
  ): ServicePdfRenderer = when (applicationProperties.serviceRenderer) {
    ServiceRenderer.ITEXT -> ITextServicePdfRenderer()
    ServiceRenderer.OPENHTMLTOPDF -> OpenHtmlServicePdfRenderer(tempDirectoryService)
    ServiceRenderer.ITEXT_INCREMENTAL -> ItextIncrementalServicePdfRenderer(tempDirectoryService, telemetryClient)
  }.also { log.info("configured ServicePdfRenderer: {}", it.javaClass.name) }
}

enum class ServiceRenderer {
  ITEXT,
  OPENHTMLTOPDF,
  ITEXT_INCREMENTAL,
}
