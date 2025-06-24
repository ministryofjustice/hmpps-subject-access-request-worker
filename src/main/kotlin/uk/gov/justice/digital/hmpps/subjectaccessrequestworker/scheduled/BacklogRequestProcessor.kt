package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

  @Async
  @Scheduled(
    fixedDelayString = "\${backlog-request.processor.interval:3}",
    initialDelayString = "\${backlog-request.processor.initial-delay:10}",
    timeUnit = TimeUnit.SECONDS,
  )
  fun processBacklogRequests() {
    backlogRequestService.claimNextRequest()?.let { backlogRequest ->
      backlogRequestService.getPendingServiceSummariesForId(backlogRequest.id)
        .takeIf { it.isNotEmpty() }
        ?.let { pendingServices ->
          log.info(
            "backlog request {} has outstanding summaries for services: {}",
            backlogRequest.id,
            pendingServices.joinToString(",") { it.serviceName },
          )
          runBlocking(Dispatchers.Default) {
            val receiverChannel = launchServiceSummaryRequestsCoroutines(backlogRequest, pendingServices)

            receiverChannel.consumeEach { summary -> backlogRequestService.addServiceSummary(backlogRequest, summary) }
            withContext(Dispatchers.IO) {
              backlogRequestService.attemptCompleteRequest(backlogRequest.id)
            }
          }
          return
        } ?: backlogRequestService.attemptCompleteRequest(backlogRequest.id)
    } ?: log.info("no backlog requests available for processing")
  }

  fun launchServiceSummaryRequestsCoroutines(
    backlogRequest: BacklogRequest,
    pendingServices: List<ServiceConfiguration>,
  ): Channel<ServiceSummary> = runBlocking {
    val channel = Channel<ServiceSummary>(capacity = pendingServices.size)
    launch {
      coroutineScope {
        pendingServices.forEach { service ->
          launch {
            val summary = backlogRequestService.getSubjectDataHeldSummary(backlogRequest, service)
            channel.send(summary)
          }
        }
      }
      channel.close()
    }
    channel
  }
}
