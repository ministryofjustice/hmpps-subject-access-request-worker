package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.StringUtils.isNotEmpty
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_RENDER_REQUEST_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_RENDER_REQUEST_FAILED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_SERVICES_SELECTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_SERVICE_SUSPENDED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_SUBMIT_RENDER_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.TempDirectoryService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.memoryUsage
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.PdfRenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.PdfService

/**
 * New world configuration worker delegates rendering service html to html-renderer service.
 */
@Service
class ReportServiceImpl(
  private val htmlRendererApiClient: HtmlRendererApiClient,
  private val prisonApiClient: PrisonApiClient,
  private val probationApiClient: ProbationApiClient,
  private val documentStorageClient: DocumentStorageClient,
  private val pdfService: PdfService,
  private val subjectAccessRequestService: SubjectAccessRequestService,
  private val tempDirectoryService: TempDirectoryService,
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

    PdfRenderRequest(
      subjectAccessRequest = subjectAccessRequest,
      subjectName = subjectName,
      reportDir = tempDirectoryService.create("${subjectAccessRequest.id}_"),
    ).use {
      val pdfPath = pdfService.renderSubjectAccessRequestPdf(it)
      log.info("pdf generated calling document store {}", memoryUsage())
      documentStorageClient.storeDocument(subjectAccessRequest, pdfPath)
      log.info("subject access request ${subjectAccessRequest.id} completed successfully")
    }
  }

  private fun generateReportHtmlForServices(subjectAccessRequest: SubjectAccessRequest) {
    val uncompletedServices = subjectAccessRequest.getSelectedServices { it.renderStatus != RenderStatus.COMPLETE }

    trackSelectedService(uncompletedServices, subjectAccessRequest)
    log.info("processing subject access request ${subjectAccessRequest.id}")

    uncompletedServices.forEach { service ->
      if (service.suspended) {
        log.warn("unable to render {} as it is suspended", service.serviceName)
        trackRenderServiceHtmlSuspended(service, subjectAccessRequest)
        subjectAccessRequestService.updateServiceStatusSuspended(subjectAccessRequest.id, service.serviceName)
      } else {
        log.info("submitted html render request for ${service.serviceName}")
        trackRenderServiceHtml(service, subjectAccessRequest)
        renderServiceHtml(subjectAccessRequest, service)
      }
    }

    subjectAccessRequestService.validateAllServicesRendered(subjectAccessRequest.id)
  }

  private fun renderServiceHtml(subjectAccessRequest: SubjectAccessRequest, service: ServiceConfiguration) {
    try {
      val response = htmlRendererApiClient.submitRenderRequest(subjectAccessRequest, service)
      subjectAccessRequestService.updateServiceStatusSuccess(
        subjectAccessRequest.id,
        service.serviceName,
        response.templateVersion,
      )

      log.info("${subjectAccessRequest.id} html render request completed successfully")
      trackRenderServiceHtmlComplete(service, subjectAccessRequest)
    } catch (error: Exception) {
      log.warn("Unable to render or update {}", service.serviceName, error)
      subjectAccessRequestService.updateServiceStatusFailed(subjectAccessRequest.id, service.serviceName)
      trackRenderServiceHtmlFailed(service, subjectAccessRequest)
    }
  }

  private fun getSubjectName(sar: SubjectAccessRequest): String {
    if (isNotEmpty(sar.nomisId)) {
      return prisonApiClient.getOffenderName(sar, sar.nomisId!!)
    }
    if (isNotEmpty(sar.ndeliusCaseReferenceId)) {
      return probationApiClient.getOffenderName(sar, sar.ndeliusCaseReferenceId!!)
    }
    throw FatalSubjectAccessRequestException(
      message = "unable to get subject ID both prison probation Ids are both null",
      subjectAccessRequest = sar,
      event = ProcessingEvent.RESOLVE_SUBJECT_NAME,
      errorCode = ErrorCode.NO_SUBJECT_ID_PROVIDED,
    )
  }

  private fun trackSelectedService(
    selectedServices: List<ServiceConfiguration>,
    subjectAccessRequest: SubjectAccessRequest,
  ) = telemetryClient.trackSarEvent(
    event = GENERATE_REPORT_SERVICES_SELECTED,
    subjectAccessRequest = subjectAccessRequest,
    "services" to selectedServices.joinToString(",") { it.serviceName },
  )

  private fun trackRenderServiceHtml(
    service: ServiceConfiguration,
    subjectAccessRequest: SubjectAccessRequest,
  ) = telemetryClient.trackSarEvent(
    event = GENERATE_REPORT_SUBMIT_RENDER_REQUEST,
    subjectAccessRequest = subjectAccessRequest,
    "serviceName" to service.serviceName,
    "serviceUrl" to service.url,
  )

  private fun trackRenderServiceHtmlComplete(
    service: ServiceConfiguration,
    subjectAccessRequest: SubjectAccessRequest,
  ) = telemetryClient.trackSarEvent(
    event = GENERATE_REPORT_RENDER_REQUEST_COMPLETED,
    subjectAccessRequest = subjectAccessRequest,
    "serviceName" to service.serviceName,
    "serviceUrl" to service.url,
  )

  private fun trackRenderServiceHtmlSuspended(
    service: ServiceConfiguration,
    subjectAccessRequest: SubjectAccessRequest,
  ) = telemetryClient.trackSarEvent(
    event = GENERATE_REPORT_SERVICE_SUSPENDED,
    subjectAccessRequest = subjectAccessRequest,
    "serviceName" to service.serviceName,
  )

  private fun trackRenderServiceHtmlFailed(
    service: ServiceConfiguration,
    subjectAccessRequest: SubjectAccessRequest,
  ) = telemetryClient.trackSarEvent(
    event = GENERATE_REPORT_RENDER_REQUEST_FAILED,
    subjectAccessRequest = subjectAccessRequest,
    "serviceName" to service.serviceName,
    "serviceUrl" to service.url,
  )
}
