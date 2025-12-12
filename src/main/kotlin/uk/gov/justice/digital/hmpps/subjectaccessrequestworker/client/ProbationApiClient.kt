package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_OFFENDER_NAME
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.PROBATION_API_AUTH_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.formatName

@Service
class ProbationApiClient(
  private val probationApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  companion object {
    private val emptyResponse = GetOffenderDetailsResponse(NameDetails(surname = "", forename = ""))
  }

  fun getOffenderName(subjectAccessRequest: SubjectAccessRequest, subjectId: String): String = try {
    val response = probationApiWebClient
      .get()
      .uri("/probation-case/$subjectId")
      .retrieve()
      .onStatus(
        { status -> status.isSameCodeAs(HttpStatus.NOT_FOUND) },
        { _ -> Mono.error(SubjectNotFoundException(subjectId)) },
      )
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          GET_OFFENDER_NAME,
          subjectAccessRequest,
          ErrorCodePrefix.PROBATION_API,
          mapOf(
            "subjectId" to subjectId,
            "uri" to "/probation-case/$subjectId",
          ),
        ),
      )
      .bodyToMono(GetOffenderDetailsResponse::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          GET_OFFENDER_NAME,
          subjectAccessRequest,
          ErrorCodePrefix.PROBATION_API,
          mapOf("subjectId" to subjectId),
        ),
      )
      // Return valid empty response when not found
      .onErrorReturn(
        SubjectNotFoundException::class.java,
        emptyResponse,
      )
      .block()

    formatName(response?.nameDetails?.forename, response?.nameDetails?.surname)
  } catch (ex: ClientAuthorizationException) {
    throw FatalSubjectAccessRequestException(
      message = "probationApiClient error authorization exception",
      cause = ex,
      event = ACQUIRE_AUTH_TOKEN,
      errorCode = PROBATION_API_AUTH_ERROR,
      subjectAccessRequest = subjectAccessRequest,
      params = null,
    )
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class GetOffenderDetailsResponse(
    @JsonProperty("name")
    val nameDetails: NameDetails?,
  )

  data class NameDetails(val surname: String? = "", val forename: String? = "")

  class SubjectNotFoundException(subjectId: String) : RuntimeException("/probation-case/$subjectId not found")
}
