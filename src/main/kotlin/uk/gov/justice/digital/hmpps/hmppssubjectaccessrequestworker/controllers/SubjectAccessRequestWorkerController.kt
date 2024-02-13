package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.controllers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services.SubjectAccessRequestWorkerService

@RestController
class SubjectAccessRequestWorkerController(@Autowired val subjectAccessRequestService: SubjectAccessRequestWorkerService) {
  @EventListener(
    ApplicationReadyEvent::class,
  )
  fun startPolling() {
    print("STARTED POLLING ")
    subjectAccessRequestService.startPolling()
  }
}
