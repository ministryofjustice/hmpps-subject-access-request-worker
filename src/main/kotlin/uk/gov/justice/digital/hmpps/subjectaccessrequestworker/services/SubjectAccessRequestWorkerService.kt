package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestDocumentStoreConflictException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

const val POLL_DELAY: Long = 10000

@Service
class SubjectAccessRequestWorkerService(
  @Autowired val getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService,
  @Autowired val documentStorageClient: DocumentStorageClient,
  @Autowired val generatePdfService: GeneratePdfService,
  @Autowired val prisonApiClient: PrisonApiClient,
  @Autowired val probationApiClient: ProbationApiClient,
  @Autowired val serviceConfigurationService: ServiceConfigurationService,
  private val subjectAccessRequestService: SubjectAccessRequestService,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    const val TIME_ELAPSED_KEY = "totalTimeElapsed"
  }

  suspend fun startPolling() {
    while (true) {
      log.info("Polling for reports...")
      pollForRequests()
    }
  }

  suspend fun pollForRequests() {
    var subjectAccessRequest: SubjectAccessRequest? = null
    val stopWatch: StopWatch = StopWatch.create()

    try {
      subjectAccessRequest = pollForNewSubjectAccessRequests()

      claimSubjectAccessRequest(subjectAccessRequest)

      stopWatch.start()
      createSubjectAccessRequestReport(subjectAccessRequest)

      withContext(Dispatchers.IO) {
        subjectAccessRequestService.updateStatus(subjectAccessRequest.id, Status.Completed)
      }

      stopWatch.stop()
      telemetryClient.trackSarEvent(
        "NewReportClaimComplete",
        subjectAccessRequest,
        TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
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
      TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
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

  suspend fun pollForNewSubjectAccessRequests(): SubjectAccessRequest {
    var subjectAccessRequests: List<SubjectAccessRequest?> = emptyList()

    while (subjectAccessRequests.isEmpty()) {
      log.info("polling in ${POLL_DELAY}ms")
      delay(POLL_DELAY)
      withContext(Dispatchers.IO) {
        subjectAccessRequests = subjectAccessRequestService.findUnclaimed()
      }
    }
    return subjectAccessRequests.first()!!
  }

  suspend fun claimSubjectAccessRequest(subjectAccessRequest: SubjectAccessRequest) {
    withContext(Dispatchers.IO) {
      subjectAccessRequestService.updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(
        subjectAccessRequest.id,
      )
    }
    log.info("report claimed with ID ${subjectAccessRequest.id} (case reference ${subjectAccessRequest.sarCaseReferenceNumber})")
    telemetryClient.trackSarEvent("NewReportClaimStarted", subjectAccessRequest, TIME_ELAPSED_KEY to "0")
  }

  fun createSubjectAccessRequestReport(subjectAccessRequest: SubjectAccessRequest) {
    val stopWatch = StopWatch.createStarted()
    telemetryClient.trackSarEvent("DoReportStarted", subjectAccessRequest, TIME_ELAPSED_KEY to "0")

    val selectedServices = serviceConfigurationService.getSelectedServices(subjectAccessRequest)

    log.info("${subjectAccessRequest.id} creating report..")
    telemetryClient.trackSarEvent(
      "CollectingServiceDataStarted",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
    )

    val dpsServiceList = getSubjectAccessRequestDataService.requestDataFromServices(
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
      TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
    )

    log.info("${subjectAccessRequest.id} fetching subject name")
    telemetryClient.trackSarEvent(
      "CollectingSubjectNameStarted",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
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
      TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
    )

    log.info("${subjectAccessRequest.id} extracted report")
    telemetryClient.trackSarEvent(
      "GeneratingPDFStreamStarted",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
    )

    val pdfStream = generatePdfService.execute(
      services = dpsServiceList,
      subjectName = subjectName,
      sar = subjectAccessRequest,
    )

    log.info("${subjectAccessRequest.id} created PDF")

    val fileSize = pdfStream.size().toString()
    telemetryClient.trackSarEvent(
      "GeneratingPDFStreamComplete",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
      "fileSize" to fileSize,
    )

    telemetryClient.trackSarEvent(
      "SavingFileStarted",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
      "fileSize" to fileSize,
    )
    uploadToDocumentStore(stopWatch, subjectAccessRequest, pdfStream)

    telemetryClient.trackSarEvent(
      "DoReportComplete",
      subjectAccessRequest,
      TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
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
        TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
        "fileSize" to postDocumentResponse.fileSize.toString(),
        "documentUuid" to postDocumentResponse.documentUuid.toString(),
      )
    } catch (ex: SubjectAccessRequestDocumentStoreConflictException) {
      log.info("document upload conflict: document id=${subjectAccessRequest.id} already exists in document store, no action will be taken")

      telemetryClient.trackSarEvent(
        "SavingFileConflictAlreadyExists",
        subjectAccessRequest,
        TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
        "outcome" to "no action required",
      )
    }
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
