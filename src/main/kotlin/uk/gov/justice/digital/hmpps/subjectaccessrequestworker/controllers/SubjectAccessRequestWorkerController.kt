package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controllers

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.web.bind.annotation.RestController

@Deprecated("functionality replaced by uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled.SubjectAccessRequestProcessor")
@RestController
class SubjectAccessRequestWorkerController {

  private val log = LoggerFactory.getLogger(this::class.java)

  @EventListener(
    ApplicationReadyEvent::class,
  )
  suspend fun startPolling() {
    // log.debug("Starting polling...")
    // pollingService.start()
  }
}
