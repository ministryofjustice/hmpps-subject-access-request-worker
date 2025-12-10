package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_LOCATION
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.ErrorCode.Companion.LOCATION_API_AUTH_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.ErrorCodePrefix.LOCATION_API_ERROR_PREFIX
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec

@Service
class LocationsApiClient(
  private val locationsApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun getLocationDetails(dpsLocationId: String): LocationDetailsResponse? = try {
    locationsApiWebClient
      .get()
      .uri("/locations/$dpsLocationId")
      .retrieve()
      .onStatus(
        { code: HttpStatusCode -> code.isSameCodeAs(HttpStatus.NOT_FOUND) },
        { Mono.error(LocationNotFoundException(dpsLocationId)) },
      )
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          event = GET_LOCATION,
          params = mapOf("dpsLocationId" to dpsLocationId),
          errorCodePrefix = LOCATION_API_ERROR_PREFIX,
        ),
      )
      .bodyToMono(LocationDetailsResponse::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          event = GET_LOCATION,
          params = mapOf("dpsLocationId" to dpsLocationId),
          errorCodePrefix = LOCATION_API_ERROR_PREFIX,
        ),
      )
      // Return null response when not found
      .onErrorResume(LocationNotFoundException::class.java) { Mono.empty() }
      .block()
  } catch (ex: ClientAuthorizationException) {
    throw FatalSubjectAccessRequestException(
      message = "locationsApiClient error authorization exception",
      cause = ex,
      event = ACQUIRE_AUTH_TOKEN,
      errorCode = LOCATION_API_AUTH_ERROR,
    )
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class LocationDetailsResponse(
    val id: String,
    val localName: String?,
    val pathHierarchy: String,
  )

  class LocationNotFoundException(dpsLocationId: String) : RuntimeException("/locations/$dpsLocationId not found")
}
