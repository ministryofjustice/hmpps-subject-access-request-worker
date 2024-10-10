package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${web-client.configuration.max-retries:0}")val maxRetries: Long,
  @Value("\${web-client.configuration.back-off:PT10S}")val backOff: String,
) {
  fun getBackoffDuration() = Duration.parse(backOff)

  override fun toString(): String {
    return "WebClientConfiguration(maxRetries=$maxRetries, backOff=$backOff)"
  }
}