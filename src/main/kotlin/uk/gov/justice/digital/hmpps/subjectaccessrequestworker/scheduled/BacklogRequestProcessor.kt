package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService
import java.util.concurrent.TimeUnit

@Component
@ConditionalOnExpression("\${backlog-request.processor.enabled:false}")
class BacklogRequestProcessor(
  val backlogRequestService: BacklogRequestService,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(
    fixedDelayString = "\${backlog-request.processor.interval:30}",
    initialDelayString = "\${backlog-request.processor.initial-delay:30}",
    timeUnit = TimeUnit.SECONDS,
  )
  suspend fun processBacklogRequests() {
    backlogRequestService.getNextToProcess()?.let { backlogRequest ->
      log.info("Processing backlog request ${backlogRequest.id}")

      if (!backlogRequestService.claimRequest(backlogRequest.id)) {
        log.info("claim request unsuccessful ${backlogRequest.id}")
        return
      }
      log.info("claim request successful ${backlogRequest.id}")

      val pendingServices = backlogRequestService.getPendingServiceSummariesForId(backlogRequest.id)
      if (pendingServices.isNotEmpty()) {
        runBlocking {
          val channel = Channel<ServiceSummary>(capacity = pendingServices.size)
          launch {
            fanOutServiceSummaryRequest(pendingServices, backlogRequest, channel)
            channel.close()
          }

          channel.consumeEach { summary ->  addServiceSummary(backlogRequest, summary)}
          attemptCompleteRequest(backlogRequest)
        }
        return
      }
      attemptCompleteRequest(backlogRequest)
    }
  }

  suspend fun fanOutServiceSummaryRequest(
    pendingService: List<ServiceConfiguration>,
    backlogRequest: BacklogRequest,
    channel: Channel<ServiceSummary>,
  ) {
    coroutineScope {
      pendingService.forEach { service ->
        launch {
          log.info(
            "sending service summary request: backlogRequestId: {}, service: {}",
            backlogRequest.id,
            service.label,
          )
          val summary = backlogRequestService.getSubjectDataHeldSummary(backlogRequest, service)
          log.info(
            "service summary request complete: backlogRequestId: {}, service: {}",
            backlogRequest.id,
            service.label,
          )
          channel.send(summary)
        }
      }
    }
  }

  fun addServiceSummary(backlogRequest: BacklogRequest, summary: ServiceSummary) {
    log.info("saving data held summary ${summary.serviceName}")
    backlogRequestService.addServiceSummary(backlogRequest, summary)
  }

  fun attemptCompleteRequest(backlogRequest: BacklogRequest) {
    val isDataHeld = backlogRequestService.isDataHeldOnSubject(backlogRequest.id)

    log.info("attempting to set backlog request: ${backlogRequest.id}, status=COMPLETED, dataHeld: $isDataHeld")
    val completeSuccessful = backlogRequestService.completeRequest(backlogRequest.id, isDataHeld)
    log.info("complete request: ${backlogRequest.id}: success? $completeSuccessful")
  }
}
