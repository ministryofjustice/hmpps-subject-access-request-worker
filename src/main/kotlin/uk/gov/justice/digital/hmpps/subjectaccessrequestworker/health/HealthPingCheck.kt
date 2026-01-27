package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.hmpps.kotlin.health.HealthPingCheck

@Component("hmppsAuth")
class HmppsAuthHealthPing(@Qualifier("hmppsAuthHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("documentApi")
class DocumentApiHealthPing(@Qualifier("documentStoreApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("prisonApi")
class PrisonApiHealthPing(@Qualifier("prisonApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("probationApi")
class ProbationApiHealthPing(@Qualifier("probationApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("locationsApi")
class LocationsApiHealthPing(@Qualifier("locationsApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("nomisMappingsApi")
class NomisMappingsApiHealthPing(@Qualifier("nomisMappingsApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("gotenbergApi")
class GotenbergHealth(@param:Qualifier("gotenbergWebClient") private val webClient: WebClient) : HealthIndicator {
  override fun health(): Health? = webClient.get()
    .uri("/health")
    .retrieve()
    .toEntity(String::class.java)
    .flatMap { Mono.just(Health.up().withDetail("HttpStatus", it.statusCode).build()) }
    .onErrorResume(WebClientResponseException::class.java) {
      Mono.just(Health.down(it).withDetail("body", it.responseBodyAsString).withDetail("HttpStatus", it.statusCode).build())
    }
    .onErrorResume(Exception::class.java) { Mono.just(Health.down(it).build()) }
    .block() ?: Health.down().withDetail("HttpStatus", "No response returned from health").build()
}
