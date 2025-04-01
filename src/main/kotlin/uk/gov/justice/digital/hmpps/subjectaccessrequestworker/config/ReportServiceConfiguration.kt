package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.GeneratePdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.GetSubjectAccessRequestDataService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.LegacyReportService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ReportService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ServiceConfigurationService

@Configuration
class ReportServiceConfiguration(
  val getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService,
  val documentStorageClient: DocumentStorageClient,
  val generatePdfService: GeneratePdfService,
  val prisonApiClient: PrisonApiClient,
  val probationApiClient: ProbationApiClient,
  val serviceConfigurationService: ServiceConfigurationService,
  private val telemetryClient: TelemetryClient,
) {

  // TODO - Using @Bean instead of @Service to prepare for future change which will have multiple implementations of
  //  this type and load the correct instance based on application.properties and @ConditionOnProperty
  @Bean
  fun subjectAccessRequestReportService(): ReportService = LegacyReportService(
    getSubjectAccessRequestDataService,
    documentStorageClient,
    generatePdfService,
    prisonApiClient,
    probationApiClient,
    serviceConfigurationService,
    telemetryClient,
  )
}
