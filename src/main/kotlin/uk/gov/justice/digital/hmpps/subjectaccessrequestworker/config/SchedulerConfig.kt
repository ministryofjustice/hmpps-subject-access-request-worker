package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class SchedulerConfig(
  @Value("\${backlog-request.processor.pool-size:10}") val backlogProcessorPoolSize: Int,
) {

  @Bean
  fun backlogScheduler(): TaskScheduler = ThreadPoolTaskScheduler().apply {
    poolSize = backlogProcessorPoolSize
    threadNamePrefix = "sar-task-pool-"
    setThreadGroupName("backlog-processor")
    initialize()
  }
}
