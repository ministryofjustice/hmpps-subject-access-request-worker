package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_OFFENDER_NAME
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.formatName

@Service
class PrisonApiClient(
  private val prisonApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  companion object {
    private val emptyResponse = GetOffenderDetailsResponse(
      lastName = "",
      firstName = "",
    )
  }

  fun getOffenderName(subjectAccessRequest: SubjectAccessRequest, subjectId: String): String = try {
    val response = prisonApiWebClient
      .get()
      .uri("/api/offenders/$subjectId")
      .retrieve()
      .onStatus(
        { code: HttpStatusCode -> code.isSameCodeAs(HttpStatus.NOT_FOUND) },
        { _ -> Mono.error(SubjectNotFoundException(subjectId)) },
      )
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          GET_OFFENDER_NAME,
          subjectAccessRequest,
          mapOf("subjectId" to subjectId),
        ),
      )
      .bodyToMono(GetOffenderDetailsResponse::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          GET_OFFENDER_NAME,
          subjectAccessRequest,
          mapOf("subjectId" to subjectId),
        ),
      )
      // Return valid empty response when not found
      .onErrorReturn(
        SubjectNotFoundException::class.java,
        emptyResponse,
      )
      .block()

    formatName(response?.firstName, response?.lastName)
  } catch (ex: ClientAuthorizationException) {
    throw FatalSubjectAccessRequestException(
      message = "prisonApiClient error authorization exception",
      cause = ex,
      event = ACQUIRE_AUTH_TOKEN,
      subjectAccessRequest = subjectAccessRequest,
      params = null,
    )
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class GetOffenderDetailsResponse(val lastName: String? = "", val firstName: String? = "")

  class SubjectNotFoundException(subjectId: String) : RuntimeException("/api/offenders/$subjectId not found")
}
