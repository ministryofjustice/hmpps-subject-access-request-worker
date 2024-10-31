package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import uk.gov.justice.hmpps.kotlin.auth.healthWebClient
import java.time.Duration

@Configuration
class WebClientConfiguration(
  @Value("\${document-storage.url}") val documentStorageApiBaseUri: String,
  @Value("\${prison-api.url}") val prisonApiBaseUri: String,
  @Value("\${probation-api.url}") val probationApiBaseUri: String,
  @Value("\${hmpps-auth.url}") val hmppsAuthBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:20s}") val timeout: Duration,
  @Value("\${api.timeout:300s}") val documentStoreTimeout: Duration,
  @Value("\${web-client.configuration.max-retries:0}") val maxRetries: Long,
  @Value("\${web-client.configuration.back-off:PT10S}") val backOff: String,
) {

  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  @Bean
  fun documentStoreApiHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.healthWebClient(documentStorageApiBaseUri, healthTimeout)

  @Bean
  fun documentStorageWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024) }
    .authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = documentStorageApiBaseUri, documentStoreTimeout)

  @Bean
  fun prisonApiHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.healthWebClient(prisonApiBaseUri, healthTimeout)

  @Bean
  fun prisonApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = prisonApiBaseUri, timeout)

  @Bean
  fun probationApiHealthWebClient(builder: WebClient.Builder): WebClient =
    builder.healthWebClient(probationApiBaseUri, healthTimeout)

  @Bean
  fun probationApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient =
    builder.authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = probationApiBaseUri, timeout)

  private var backOffDuration: Duration = Duration.parse(backOff)

  fun getBackoffDuration() = backOffDuration

  override fun toString(): String {
    return "WebClientConfiguration(maxRetries=$maxRetries, backOff=$backOff)"
  }
}
