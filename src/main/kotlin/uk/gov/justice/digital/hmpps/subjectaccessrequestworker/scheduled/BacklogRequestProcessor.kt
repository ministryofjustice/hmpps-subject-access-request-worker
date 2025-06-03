package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService
import java.util.concurrent.TimeUnit

@Component
class BacklogRequestProcessor(
  val backlogRequestService: BacklogRequestService,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(
    fixedDelay = 30,
    timeUnit = TimeUnit.SECONDS,
    initialDelay = 30,
  )
  fun processBacklogRequests() {
    backlogRequestService.getNextToProcess()?.let { backlogRequest ->
      log.info("Processing backlog request ${backlogRequest.id}")

      if (!backlogRequestService.claimRequest(backlogRequest.id)) {
        log.info("claim request unsuccessful ${backlogRequest.id}")
        return
      }
      log.info("claim request successful ${backlogRequest.id}")

      val pendingSummaries = backlogRequestService.getPendingServiceSummariesForId(backlogRequest.id)

      pendingSummaries.forEach { service ->
        log.info("requesting data held summary for ${service.serviceName}")
        val summary = backlogRequestService.getServiceSummary(backlogRequest, service)

        log.info("saving data held summary for ${service.serviceName}")
        backlogRequestService.addServiceSummary(
          request = backlogRequest,
          summary = summary,
        )
      }

      log.info("completing backlog request ${backlogRequest.id}")
      backlogRequestService.completeRequest(backlogRequest.id)
    }
  }
}