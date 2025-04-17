package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.StringUtils.isNotEmpty
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayOutputStream

/**
 * New world configuration worker delegates rendering service html to html-renderer service.
 */
@Service
@ConditionalOnProperty(name = ["html-renderer.enabled"], havingValue = "true")
class ReportServiceImpl(
  private val htmlRendererApiClient: HtmlRendererApiClient,
  private val prisonApiClient: PrisonApiClient,
  private val probationApiClient: ProbationApiClient,
  private val documentStorageClient: DocumentStorageClient,
  private val serviceConfigurationService: ServiceConfigurationService,
  private val pdfService: PdfService,
  private val telemetryClient: TelemetryClient,
) : ReportService {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  init {
    log.info("htmlRenderEnabled=true configuring worker with new reportService")
  }

  override suspend fun generateReport(subjectAccessRequest: SubjectAccessRequest) {
    generateReportHtmlForServices(subjectAccessRequest)
    val subjectName = getSubjectName(subjectAccessRequest).also { log.info("subject name: $it") }

    renderPdfForSelectedServices(subjectAccessRequest, subjectName).use { pdfStream ->
      documentStorageClient.storeDocument(subjectAccessRequest, pdfStream).also {
        log.info("subject access request ${subjectAccessRequest.id} completed successfully")
      }
    }
  }

  private fun generateReportHtmlForServices(subjectAccessRequest: SubjectAccessRequest) {
    val selectedServices = serviceConfigurationService.getSelectedServices(subjectAccessRequest)

    trackSelectedService(selectedServices, subjectAccessRequest)
    log.info("processing subject access request ${subjectAccessRequest.id}")

    selectedServices.forEach { service ->
      log.info("submitted html render request for ${service.name!!}")
      trackRenderServiceHtml(service, subjectAccessRequest)

      htmlRendererApiClient.submitRenderRequest(subjectAccessRequest, service)
      log.info("${subjectAccessRequest.id} html render request completed successfully")
      trackRenderServiceHtmlComplete(service, subjectAccessRequest)
    }
  }

  private fun getSubjectName(sar: SubjectAccessRequest): String {
    if (isNotEmpty(sar.nomisId)) {
      return prisonApiClient.getOffenderName(sar, sar.nomisId!!)
    }
    if (isNotEmpty(sar.ndeliusCaseReferenceId)) {
      return probationApiClient.getOffenderName(sar, sar.ndeliusCaseReferenceId!!)
    }
    throw RuntimeException("unable to get subject ID both prison probation Ids are both null")
  }

  private suspend fun renderPdfForSelectedServices(
    subjectAccessRequest: SubjectAccessRequest,
    subjectName: String,
  ): ByteArrayOutputStream = pdfService.renderSubjectAccessRequestPdf(
    PdfService.PdfRenderRequest(
      subjectAccessRequest,
      subjectName,
    ),
  )

  private fun trackSelectedService(
    selectedServices: List<DpsService>,
    subjectAccessRequest: SubjectAccessRequest,
  ) = telemetryClient.trackSarEvent(
    "SelectedServices",
    subjectAccessRequest,
    "services" to selectedServices.map { it.name }.joinToString(","),
  )

  private fun trackRenderServiceHtml(
    service: DpsService,
    subjectAccessRequest: SubjectAccessRequest,
  ) = telemetryClient.trackSarEvent(
    "RenderHtmlForService",
    subjectAccessRequest,
    "serviceName" to (service.name ?: "NA"),
    "serviceUrl" to service.url.toString(),
  )

  private fun trackRenderServiceHtmlComplete(
    service: DpsService,
    subjectAccessRequest: SubjectAccessRequest,
  ) = telemetryClient.trackSarEvent(
    "RenderHtmlForService",
    subjectAccessRequest,
    "serviceName" to (service.name ?: "NA"),
    "serviceUrl" to service.url.toString(),
  )
}
