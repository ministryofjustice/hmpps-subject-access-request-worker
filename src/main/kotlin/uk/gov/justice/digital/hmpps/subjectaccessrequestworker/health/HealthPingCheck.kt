package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
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

@Component("gotenberg")
class GotenbergHealth(@Qualifier("gotenbergWebClient") webClient: WebClient) : HealthPingCheck(webClient)
