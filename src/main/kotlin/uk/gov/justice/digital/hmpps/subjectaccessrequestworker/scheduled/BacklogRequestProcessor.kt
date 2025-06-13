package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
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
  fun processBacklogRequests() {
    backlogRequestService.getNextToProcess()?.let { backlogRequest ->
      log.info("Processing backlog request ${backlogRequest.id}")

      if (!backlogRequestService.claimRequest(backlogRequest.id)) {
        log.info("claim request unsuccessful ${backlogRequest.id}")
        return
      }
      log.info("claim request successful ${backlogRequest.id}")

      val pendingServices = backlogRequestService.getPendingServiceSummariesForId(backlogRequest.id)

      pendingServices.forEach { service ->
        log.info("requesting data held summary for ${service.serviceName}")

        val summary = backlogRequestService.getSubjectDataHeldSummary(backlogRequest, service)

        log.info("saving data held summary for ${service.serviceName}")
        backlogRequestService.addServiceSummary(backlogRequest, summary)
      }

      val isDataHeld = backlogRequestService.isDataHeldOnSubject(backlogRequest.id)

      log.info("completing backlog request ${backlogRequest.id}, dataHeld: $isDataHeld")
      backlogRequestService.completeRequest(backlogRequest.id, isDataHeld).also {
        log.info("completed backlog request ${backlogRequest.id}: result=$it")
      }
    }
  }
}
