package uk.gov.justice.digital.hmpps.subjectaccessrequestworker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SubjectAccessRequestWorker

fun main(args: Array<String>) {
  runApplication<SubjectAccessRequestWorker>(*args)
}
