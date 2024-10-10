package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${web-client.configuration.max-retries}")val maxRetries: Long,
  @Value("\${web-client.configuration.back-off}")val backOff: Long,
) {
  fun backOffInSeconds() = Duration.ofSeconds(backOff)

  override fun toString(): String {
    return "WebClientConfiguration(maxRetries=$maxRetries, backOff=$backOff)"
  }
}