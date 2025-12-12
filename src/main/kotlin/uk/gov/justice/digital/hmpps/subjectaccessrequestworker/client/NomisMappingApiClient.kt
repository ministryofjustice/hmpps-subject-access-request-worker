package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_LOCATION_MAPPING
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec

@Service
class NomisMappingApiClient(
  private val nomisMappingsApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun getNomisLocationMapping(nomisLocationId: Int): NomisLocationMapping? = try {
    nomisMappingsApiWebClient
      .get()
      .uri("/api/locations/nomis/$nomisLocationId")
      .retrieve()
      .onStatus(
        { code: HttpStatusCode -> code.isSameCodeAs(HttpStatus.NOT_FOUND) },
        { Mono.error(NomisLocationMappingNotFoundException(nomisLocationId)) },
      )
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          event = GET_LOCATION_MAPPING,
          params = mapOf("nomisLocationId" to nomisLocationId),
          errorCodePrefix = ErrorCodePrefix.NOMIS_API,
        ),
      )
      .bodyToMono(NomisLocationMapping::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          event = GET_LOCATION_MAPPING,
          params = mapOf("nomisLocationId" to nomisLocationId),
          errorCodePrefix = ErrorCodePrefix.NOMIS_API,
        ),
      )
      // Return null when not found
      .onErrorResume(NomisLocationMappingNotFoundException::class.java) { Mono.empty() }
      .block()
  } catch (ex: ClientAuthorizationException) {
    throw FatalSubjectAccessRequestException(
      message = "nomisMappingsApiClient error authorization exception",
      cause = ex,
      event = ACQUIRE_AUTH_TOKEN,
      errorCode = ErrorCode.NOMIS_API_AUTH_ERROR,
      params = null,
    )
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class NomisLocationMapping(
    val dpsLocationId: String,
    val nomisLocationId: Int,
  )

  class NomisLocationMappingNotFoundException(
    nomisLocationId: Int,
  ) : RuntimeException("/api/locations/nomis/$nomisLocationId not found")
}
