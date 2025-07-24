package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.reactive.function.client.ExchangeStrategies
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
  @Value("\${locations-api.url}") val locationsApiBaseUri: String,
  @Value("\${nomis-mappings-api.url}") val nomisMappingsApiBaseUri: String,
  @Value("\${sar-html-renderer-api.url}") val sarHtmlRendererApiBaseUri: String,
  @Value("\${gotenberg-api.url}") private val gotenbergBaseUri: String,
  @Value("\${api.health-timeout:2s}") val healthTimeout: Duration,
  @Value("\${api.timeout:20s}") val timeout: Duration,
  @Value("\${document-store.timeout:300s}") val documentStoreTimeout: Duration,
  @Value("\${web-client.configuration.max-retries:0}") val maxRetries: Long,
  @Value("\${web-client.configuration.back-off:PT10S}") val backOff: String,
  @Value("\${gotenberg-api.buffer-limit:10}") val gotenbergApiBufferLimit: Int,
) {

  @Bean
  fun hmppsAuthHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(hmppsAuthBaseUri, healthTimeout)

  @Bean
  fun documentStoreApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(documentStorageApiBaseUri, healthTimeout)

  @Bean
  fun documentStorageWebClient(
    authorizedClientManager: OAuth2AuthorizedClientManager,
    builder: WebClient.Builder,
  ): WebClient = builder.codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024) }
    .authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = documentStorageApiBaseUri, documentStoreTimeout)

  @Bean
  fun prisonApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(prisonApiBaseUri, healthTimeout)

  @Bean
  fun prisonApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = prisonApiBaseUri, timeout)

  @Bean
  fun probationApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(probationApiBaseUri, healthTimeout)

  @Bean
  fun probationApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = probationApiBaseUri, timeout)

  @Bean
  fun dynamicWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024) }
    .authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = "http", timeout = timeout)

  @Bean
  fun locationsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(locationsApiBaseUri, healthTimeout)

  @Bean
  fun locationsApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = locationsApiBaseUri, timeout)

  @Bean
  fun nomisMappingsApiHealthWebClient(builder: WebClient.Builder): WebClient = builder.healthWebClient(nomisMappingsApiBaseUri, healthTimeout)

  @Bean
  fun nomisMappingsApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = nomisMappingsApiBaseUri, timeout)

  @Bean
  fun sarHtmlRendererApiWebClient(authorizedClientManager: OAuth2AuthorizedClientManager, builder: WebClient.Builder): WebClient = builder
    .authorisedWebClient(authorizedClientManager, registrationId = "sar-client", url = sarHtmlRendererApiBaseUri, timeout)

  @Bean
  fun gotenbergWebClient(): WebClient = getPlainWebClient(WebClient.builder(), gotenbergBaseUri)

  private var backOffDuration: Duration = Duration.parse(backOff)

  fun getBackoffDuration() = backOffDuration

  override fun toString(): String = "WebClientConfiguration(maxRetries=$maxRetries, backOff=$backOff)"

  private fun getPlainWebClient(builder: WebClient.Builder, rootUri: String): WebClient = builder
    .baseUrl(rootUri).exchangeStrategies(
      ExchangeStrategies.builder()
        .codecs { config -> config.defaultCodecs().maxInMemorySize(gotenbergApiBufferLimit * 1024 * 1024) }.build(),
    ).build()
}
