package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.commons.lang3.StringUtils
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_OFFENDER_NAME
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec

@Service
class ProbationApiClient(
  private val probationApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  companion object {
    private val emptyResponse = GetOffenderDetailsResponse(NameDetails(surname = "", forename = ""))
  }

  fun getOffenderName(subjectAccessRequest: SubjectAccessRequest, subjectId: String): String {
    return try {
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
            mapOf("subjectId" to subjectId),
          ),
        )
        // Return valid empty response when not found
        .onErrorReturn(
          SubjectNotFoundException::class.java,
          emptyResponse,
        )
        .block()

      response?.nameDetails?.formatName() ?: ""
    } catch (ex: ClientAuthorizationException) {
      throw FatalSubjectAccessRequestException(
        message = "probationApiClient error authorization exception",
        cause = ex,
        event = ACQUIRE_AUTH_TOKEN,
        subjectAccessRequest = subjectAccessRequest,
        params = mapOf(
          "cause" to ex.cause?.message,
        ),
      )
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class GetOffenderDetailsResponse(
    @JsonProperty("name")
    val nameDetails: NameDetails?,
  )

  data class NameDetails(val surname: String? = "", val forename: String? = "") {
    /**
     * Return the offender name formatted as "SURNAME, forename" if both values present. Otherwise, return empty string
     */
    fun formatName(): String {
      if (StringUtils.isEmpty(surname) || StringUtils.isEmpty(forename)) {
        return ""
      }

      return "${surname!!.uppercase()}, ${forename!!.lowercase().replaceFirstChar { it.titlecase() }}"
    }
  }

  class SubjectNotFoundException(subjectId: String) : RuntimeException("/probation-case/$subjectId not found")
}
