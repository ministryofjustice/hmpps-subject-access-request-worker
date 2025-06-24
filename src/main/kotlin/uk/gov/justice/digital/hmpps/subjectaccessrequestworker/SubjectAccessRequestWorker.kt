package uk.gov.justice.digital.hmpps.subjectaccessrequestworker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
class SubjectAccessRequestWorker

fun main(args: Array<String>) {
  runApplication<SubjectAccessRequestWorker>(*args)
}
