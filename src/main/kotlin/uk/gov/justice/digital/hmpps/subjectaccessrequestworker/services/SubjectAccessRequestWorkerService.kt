package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import io.micrometer.common.util.StringUtils
import io.sentry.Sentry
import kotlinx.coroutines.delay
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestDocumentStoreConflictException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.ConfigOrderHelper
import java.io.ByteArrayOutputStream

const val POLL_DELAY: Long = 10000

@Service
class SubjectAccessRequestWorkerService(
  @Autowired val sarGateway: SubjectAccessRequestGateway,
  @Autowired val getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService,
  @Autowired val documentStorageClient: DocumentStorageClient,
  @Autowired val generatePdfService: GeneratePdfService,
  @Autowired val prisonApiClient: PrisonApiClient,
  @Autowired val probationApiClient: ProbationApiClient,
  @Autowired val configOrderHelper: ConfigOrderHelper,
  @Value("\${sar-api.url}") private val sarUrl: String,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    const val TIME_ELAPSED_KEY = "totalTimeElapsed"
  }

  suspend fun startPolling() {
    while (true) {
      log.info("Polling for reports...")
      doPoll()
    }
  }

  suspend fun doPoll() {
    var subjectAccessRequest: SubjectAccessRequest? = null
    val stopWatch: StopWatch = StopWatch.create()

    try {
      val webClient = sarGateway.getClient(sarUrl)
      subjectAccessRequest = pollForNewSubjectAccessRequests(webClient)

      claimSubjectAccessRequest(webClient, subjectAccessRequest)

      stopWatch.start()
      doReport(subjectAccessRequest)
      sarGateway.complete(webClient, subjectAccessRequest)
      stopWatch.stop()
      telemetryClient.trackSarEvent(
        "NewReportClaimComplete",
        subjectAccessRequest,
        TIME_ELAPSED_KEY to stopWatch.time.toString(),
      )
    } catch (exception: Exception) {
      handleError(stopWatch, subjectAccessRequest, exception)
    }
  }

  fun handleError(stopWatch: StopWatch, subjectAccessRequest: SubjectAccessRequest?, exception: Exception) {
    val errorMessage = buildString {
      append("subjectAccessRequest ")
      subjectAccessRequest?.id?.let { append("id=$it ") }
      subjectAccessRequest?.sarCaseReferenceNumber?.let { append("sarCaseReferenceNumber=$it ") }
      append("failed with error: ${exception.message}")
    }
    log.error(errorMessage, exception)
    exception.printStackTrace()

    telemetryClient.trackSarEvent(
      "ReportFailedWithError",
      subjectAccessRequest,
      "error" to (exception.message ?: ""),
      TIME_ELAPSED_KEY to stopWatch.time.toString(),
    )

    val sarException = if (exception is SubjectAccessRequestException) {
      exception
    } else {
      SubjectAccessRequestException(
        message = "subject access request threw unexpected error",
        cause = exception,
        event = null,
        subjectAccessRequest = subjectAccessRequest,
        mapOf(
          "sarCaseReferenceNumber" to subjectAccessRequest?.sarCaseReferenceNumber,
        ),
      )
    }
    Sentry.captureException(sarException)
  }

  suspend fun pollForNewSubjectAccessRequests(client: WebClient): SubjectAccessRequest {
    var response: Array<SubjectAccessRequest>? = emptyArray()

    while (response.isNullOrEmpty()) {
      log.info("polling in ${POLL_DELAY}ms")
      delay(POLL_DELAY)
      response = sarGateway.getUnclaimed(client)
    }
    return response.first()
  }

  suspend fun claimSubjectAccessRequest(webClient: WebClient, subjectAccessRequest: SubjectAccessRequest) {
    sarGateway.claim(webClient, subjectAccessRequest)
    log.info("report claimed with ID ${subjectAccessRequest.id} (case reference ${subjectAccessRequest.sarCaseReferenceNumber})")
    telemetryClient.trackSarEvent("NewReportClaimStarted", subjectAccessRequest, TIME_ELAPSED_KEY to "0")
  }

  fun doReport(subjectAccessRequest: SubjectAccessRequest) {
    val stopWatch = StopWatch.createStarted()
    telemetryClient.trackSarEvent("DoReportStarted", subjectAccessRequest, TIME_ELAPSED_KEY to "0")

    val selectedServices = getServiceDetails(subjectAccessRequest)

    log.info("${subjectAccessRequest.id} creating report..")
    telemetryClient.trackSarEvent(
      "CollectingServiceDataStarted",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.time.toString(),
    )

    val dpsServiceList = getSubjectAccessRequestDataService.execute(
      selectedServices,
      subjectAccessRequest.nomisId,
      subjectAccessRequest.ndeliusCaseReferenceId,
      subjectAccessRequest.dateFrom,
      subjectAccessRequest.dateTo,
      subjectAccessRequest,
    )

    telemetryClient.trackSarEvent(
      "CollectingServiceDataComplete",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.time.toString(),
    )

    log.info("${subjectAccessRequest.id} fetching subject name")
    telemetryClient.trackSarEvent(
      "CollectingSubjectNameStarted",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.time.toString(),
    )

    var subjectName: String = getSubjectName(
      subjectAccessRequest,
      subjectAccessRequest.nomisId,
      subjectAccessRequest.ndeliusCaseReferenceId,
    )
    if (StringUtils.isEmpty(subjectName)) {
      subjectName = "No subject name found"
    }

    telemetryClient.trackSarEvent(
      "CollectingSubjectNameComplete",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.time.toString(),
    )

    log.info("${subjectAccessRequest.id} extracted report")
    telemetryClient.trackSarEvent(
      "GeneratingPDFStreamStarted",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.time.toString(),
    )

    val pdfStream = generatePdfService.execute(
      dpsServiceList,
      subjectAccessRequest.nomisId,
      subjectAccessRequest.ndeliusCaseReferenceId,
      subjectAccessRequest.sarCaseReferenceNumber,
      subjectName,
      subjectAccessRequest.dateFrom,
      subjectAccessRequest.dateTo,
      subjectAccessRequest,
    )
    log.info("${subjectAccessRequest.id} created PDF")

    val fileSize = pdfStream.size().toString()
    telemetryClient.trackSarEvent(
      "GeneratingPDFStreamComplete",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.time.toString(),
      "fileSize" to fileSize,
    )

    telemetryClient.trackSarEvent(
      "SavingFileStarted",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.time.toString(),
      "fileSize" to fileSize,
    )
    uploadToDocumentStore(stopWatch, subjectAccessRequest, pdfStream)

    telemetryClient.trackSarEvent(
      "DoReportComplete",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.time.toString(),
    )
  }

  /**
   * Attempt to upload the document to the DocumentStore. If a document with the specified ID already exists no action
   * will be taken. The "new" document will not be uploaded and the SAR request will marked complete.
   */
  fun uploadToDocumentStore(
    stopWatch: StopWatch,
    subjectAccessRequest: SubjectAccessRequest,
    pdfStream: ByteArrayOutputStream,
  ) {
    try {
      val postDocumentResponse = this.documentStorageClient.storeDocument(subjectAccessRequest, pdfStream)
      log.info("${subjectAccessRequest.id} successfully uploaded to document store documentUuid: ${postDocumentResponse.documentUuid}")

      telemetryClient.trackSarEvent(
        "SavingFileComplete",
        subjectAccessRequest,
        TIME_ELAPSED_KEY to stopWatch.time.toString(),
        "fileSize" to postDocumentResponse.fileSize.toString(),
        "documentUuid" to postDocumentResponse.documentUuid.toString(),
      )
    } catch (ex: SubjectAccessRequestDocumentStoreConflictException) {
      log.info("document upload conflict: document id=${subjectAccessRequest.id} already exists in document store, no action will be taken")

      telemetryClient.trackSarEvent(
        "SavingFileConflictAlreadyExists",
        subjectAccessRequest,
        TIME_ELAPSED_KEY to stopWatch.time.toString(),
        "outcome" to "no action required",
      )
    }
  }

  fun getServicesMap(subjectAccessRequest: SubjectAccessRequest): MutableMap<String, String> {
    val services = subjectAccessRequest.services
    val serviceMap = mutableMapOf<String, String>()

    val serviceNames =
      services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 == 0 }
    val serviceUrls =
      services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 != 0 }

    for (serviceName in serviceNames) {
      serviceMap[serviceName] = serviceUrls[serviceNames.indexOf(serviceName)]
    }
    return serviceMap
  }

  fun getServiceDetails(
    subjectAccessRequest: SubjectAccessRequest,
  ): List<DpsService> {
    val servicesMap = getServicesMap(subjectAccessRequest)

    val selectedServices = configOrderHelper.getDpsServices(servicesMap)

    val serviceConfigObject = configOrderHelper.extractServicesConfig("servicesConfig.yaml")

    for (service in selectedServices) {
      if (serviceConfigObject != null) {
        for (configService in serviceConfigObject.dpsServices) {
          if (configService.name == service.name) {
            service.businessName = configService.businessName
            service.orderPosition = configService.orderPosition
          }
        }
      }
    }
    return selectedServices
  }

  fun getSubjectName(subjectAccessRequest: SubjectAccessRequest, prisonId: String?, probationId: String?): String {
    if (prisonId !== null) {
      return prisonApiClient.getOffenderName(subjectAccessRequest, prisonId)
    }
    if (probationId !== null) {
      return probationApiClient.getOffenderName(subjectAccessRequest, probationId)
    }
    throw RuntimeException("Prison and Probation IDs are both null")
  }
}
