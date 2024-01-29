package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HmppsSubjectAccessRequestWorker

fun main(args: Array<String>) {
  runApplication<HmppsSubjectAccessRequestWorker>(*args)
}
