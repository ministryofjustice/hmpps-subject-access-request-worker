package uk.gov.justice.digital.hmpps.subjectaccessrequestworker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SubjectAccessRequestWorker

fun main(args: Array<String>) {
  runApplication<SubjectAccessRequestWorker>(*args)
}
