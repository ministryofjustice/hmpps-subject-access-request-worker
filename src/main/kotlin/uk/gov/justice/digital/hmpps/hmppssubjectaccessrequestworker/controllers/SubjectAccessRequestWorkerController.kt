package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.controllers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services.SubjectAccessRequestWorkerService
import kotlin.math.log

@RestController
@Profile("!test")
class SubjectAccessRequestWorkerController(@Autowired val subjectAccessRequestService: SubjectAccessRequestWorkerService) {
  @EventListener(
    ApplicationReadyEvent::class,
  )
  fun startPolling() {
    subjectAccessRequestService.startPolling()
  }
}
