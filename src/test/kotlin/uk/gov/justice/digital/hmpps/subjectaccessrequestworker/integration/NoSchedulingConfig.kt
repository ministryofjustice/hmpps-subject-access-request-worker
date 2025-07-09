package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.NoOpTaskScheduler

@TestConfiguration
class NoSchedulingConfig {
  @Bean
  @Primary
  fun taskScheduler(): TaskScheduler = NoOpTaskScheduler()
}
