package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_OFFENDER_NAME
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.util.Locale

@Service
class ProbationApiClient(
  private val probationApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun getOffenderName(subjectAccessRequest: SubjectAccessRequest, subjectId: String): String {
    return try {
      val response = probationApiWebClient
        .get()
        .uri("/probation-case/$subjectId")
        .retrieve()
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
        .bodyToMono(Map::class.java)
        .retryWhen(
          webClientRetriesSpec.retry5xxAndClientRequestErrors(
            GET_OFFENDER_NAME,
            subjectAccessRequest,
            mapOf("subjectId" to subjectId),
          ),
        )
        .block()

      val nameMap = response["name"] as Map<String, String>

      "${nameMap["surname"]?.uppercase()}, ${
        nameMap["forename"]?.lowercase()
          ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
      }"
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
}
