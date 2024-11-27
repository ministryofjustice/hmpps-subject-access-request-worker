package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controllers

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.SubjectAccessRequestWorkerService

@RestController
class SubjectAccessRequestWorkerController(@Autowired val subjectAccessRequestService: SubjectAccessRequestWorkerService) {

  private val log = LoggerFactory.getLogger(this::class.java)

  @EventListener(
    ApplicationReadyEvent::class,
  )
  suspend fun startPolling() {
    log.info("Starting polling...")
    subjectAccessRequestService.startPolling()
  }
}
