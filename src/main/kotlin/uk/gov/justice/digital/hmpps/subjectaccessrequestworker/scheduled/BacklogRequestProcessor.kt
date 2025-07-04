package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.scheduling.annotation.Async
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

  @Async("backlogScheduler")
  @Scheduled(
    fixedRateString = "\${backlog-request.processor.interval:3}",
    initialDelayString = "\${backlog-request.processor.initial-delay:10}",
    timeUnit = TimeUnit.SECONDS,
  )
  fun processBacklogRequests() {
    backlogRequestService.claimNextRequest()?.let { request ->
      backlogRequestService.getServicesToQueryForRequest(request.id)
        .takeIf { it.isNotEmpty() }
        ?.let { servicesToQuery ->
          launchAsyncServiceSummaryRequests(request, servicesToQuery)
        }
        ?: backlogRequestService.attemptCompleteRequest(request.id)
    } ?: log.info("no backlog requests available for processing")
  }

  private fun launchAsyncServiceSummaryRequests(
    request: BacklogRequest,
    services: List<ServiceConfiguration>,
  ) {
    log.info(
      "backlog request {} has outstanding summaries for services: {}",
      request.id,
      services.joinToString(",") { it.serviceName },
    )

    runBlocking(Dispatchers.Default) {
      val channel = Channel<ServiceSummary>()

      // Receive the results from each Coroutine
      launch {
        channel.consumeEach { summary -> backlogRequestService.addServiceSummary(request, summary) }
        backlogRequestService.attemptCompleteRequest(request.id)
      }

      // Fanout to send requests to each service
      supervisorScope {
        val deferredResults: List<Deferred<ServiceSummary>> = services.map { service ->
          async { backlogRequestService.getSubjectDataHeldSummary(request, service) }
        }

        // Wait for all to complete and catch and log any errors.
        deferredResults.forEach { result ->
          launch {
            try {
              val summary = result.await()
              channel.send(summary)
            } catch (e: Exception) {
              // No action required - the processor will identify any failed requests that need to be tried again.
              log.error("get service summary for backlogRequest={} errored, message={}", request.id, e.message)
            }
          }
        }
      }
      channel.close()
      log.info("service summary fanout complete for backlogRequest={}, attempting to complete request", request.id)
    }
  }
}
