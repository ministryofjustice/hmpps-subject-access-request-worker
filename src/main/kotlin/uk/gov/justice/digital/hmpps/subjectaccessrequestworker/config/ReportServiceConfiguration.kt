package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.GeneratePdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.GetSubjectAccessRequestDataService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.LegacyReportService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.PdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ReportService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ReportServiceImpl
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ServiceConfigurationService

@Configuration
class ReportServiceConfiguration(
  @Value("\${html-renderer.enabled}") private val htmlRenderEnabled: Boolean,
  private val getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService,
  private val documentStorageClient: DocumentStorageClient,
  private val generatePdfService: GeneratePdfService,
  private val prisonApiClient: PrisonApiClient,
  private val probationApiClient: ProbationApiClient,
  private val serviceConfigurationService: ServiceConfigurationService,
  private val telemetryClient: TelemetryClient,
  private val htmlRendererApiClient: HtmlRendererApiClient,
  private val pdfService: PdfService,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(ReportServiceConfiguration::class.java)
  }

  @Bean
  fun reportService(): ReportService {
    if (htmlRenderEnabled) {
      return ReportServiceImpl(
        htmlRendererApiClient,
        prisonApiClient,
        probationApiClient,
        documentStorageClient,
        serviceConfigurationService,
        pdfService,
      ).also { log.info("htmlRenderEnabled=true configuring worker with new reportService") }
    }
    return LegacyReportService(
      getSubjectAccessRequestDataService,
      documentStorageClient,
      generatePdfService,
      prisonApiClient,
      probationApiClient,
      serviceConfigurationService,
      telemetryClient,
    ).also { log.info("htmlRenderEnabled=false configuring worker with legacy reportService") }
  }
}
