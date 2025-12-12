package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting.AlertsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.REQUEST_CLAIMED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.REQUEST_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ReportService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.SubjectAccessRequestService
import java.util.concurrent.TimeUnit

@Component
class SubjectAccessRequestProcessor(
  private val subjectAccessRequestService: SubjectAccessRequestService,
  private val reportService: ReportService,
  private val alertsService: AlertsService,
  private val telemetryClient: TelemetryClient,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    const val TIME_ELAPSED_KEY = "totalTimeElapsed"
  }

  @Scheduled(
    fixedDelayString = "\${scheduled.subject-access-request-processor.interval-seconds:30}",
    timeUnit = TimeUnit.SECONDS,
    initialDelayString = "\${scheduled.subject-access-request-processor.initial-delay-seconds:30}",
  )
  suspend fun execute() {
    log.info("checking for available subject access requests to process")

    var subjectAccessRequest: SubjectAccessRequest? = null
    val stopWatch: StopWatch = StopWatch.create()

    try {
      subjectAccessRequest = getNextRequestOrNull()

      subjectAccessRequest?.let {
        processRequest(it, stopWatch)
      } ?: run {
        log.info("no available subject access requests to process")
      }
    } catch (ex: Exception) {
      handleError(stopWatch, subjectAccessRequest, ex)
    }
  }

  private fun getNextRequestOrNull(): SubjectAccessRequest? = subjectAccessRequestService
    .findUnclaimed()
    .takeIf { it.isNotEmpty() }
    ?.firstOrNull()

  private suspend fun processRequest(subjectAccessRequest: SubjectAccessRequest, stopWatch: StopWatch) {
    stopWatch.start()

    subjectAccessRequestService.updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(subjectAccessRequest.id)
    reportClaimedEvent(subjectAccessRequest)
    log.info(
      "report claimed with ID {} (case reference {})",
      subjectAccessRequest.id,
      subjectAccessRequest.sarCaseReferenceNumber,
    )

    reportService.generateReport(subjectAccessRequest)

    subjectAccessRequestService.updateStatus(subjectAccessRequest.id, Status.Completed)

    stopWatch.stop()
    reportCompletedEvent(subjectAccessRequest, stopWatch)
  }

  private fun handleError(stopWatch: StopWatch, subjectAccessRequest: SubjectAccessRequest?, ex: Exception) {
    log.error(buildErrorMessage(subjectAccessRequest, ex), ex)

    reportErrorEvent(subjectAccessRequest, ex, stopWatch)

    val sarException = if (ex is SubjectAccessRequestException) {
      ex
    } else {
      createSubjectAccessRequestExceptionFor(subjectAccessRequest, ex)
    }
    alertsService.raiseReportErrorAlert(sarException)
  }

  private fun reportClaimedEvent(
    subjectAccessRequest: SubjectAccessRequest?,
  ) = telemetryClient.trackSarEvent(
    event = REQUEST_CLAIMED,
    subjectAccessRequest = subjectAccessRequest,
  )

  private fun reportCompletedEvent(
    subjectAccessRequest: SubjectAccessRequest?,
    stopWatch: StopWatch,
  ) = telemetryClient.trackSarEvent(
    event = REQUEST_COMPLETED,
    subjectAccessRequest = subjectAccessRequest,
    TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
  )

  private fun reportErrorEvent(
    subjectAccessRequest: SubjectAccessRequest?,
    exception: Exception,
    stopWatch: StopWatch,
  ) = telemetryClient.trackSarException(
    ex = exception,
    subjectAccessRequest = subjectAccessRequest,
    "error" to getErrorMessage(exception),
    TIME_ELAPSED_KEY to stopWatch.getTime(TimeUnit.MILLISECONDS).toString(),
  )

  private fun getErrorMessage(ex: Exception): String {
    val exceptionName: String = ex.javaClass.simpleName

    return ex.message?.takeIf {
      it.isNotBlank()
    } ?: ex.cause?.let {
      "${exceptionName}_${it::class.java.simpleName}"
    } ?: exceptionName
  }

  private fun createSubjectAccessRequestExceptionFor(
    subjectAccessRequest: SubjectAccessRequest?,
    ex: Exception,
  ) = SubjectAccessRequestException(
    message = "subject access request threw unexpected error",
    cause = ex,
    event = null,
    errorCode = ErrorCode.INTERNAL_SERVER_ERROR,
    subjectAccessRequest = subjectAccessRequest,
    mapOf(
      "sarCaseReferenceNumber" to subjectAccessRequest?.sarCaseReferenceNumber,
    ),
  )

  private fun buildErrorMessage(subjectAccessRequest: SubjectAccessRequest?, ex: Exception) = buildString {
    append("subjectAccessRequest ")
    subjectAccessRequest?.id?.let { append("id=$it ") }
    subjectAccessRequest?.sarCaseReferenceNumber?.let { append("sarCaseReferenceNumber=$it ") }
    append("failed with error: ${ex.message}")
  }
}
