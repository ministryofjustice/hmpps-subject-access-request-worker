package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import org.apache.tomcat.util.json.JSONParser
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
class PrisonApiClient(
  private val prisonApiWebClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun getOffenderName(subjectAccessRequest: SubjectAccessRequest, subjectId: String): String {
    return try {
      val response = prisonApiWebClient
        .get()
        .uri("/api/offenders/$subjectId")
        .retrieve()
        .onStatus(
          webClientRetriesSpec.is4xxStatus(),
          webClientRetriesSpec.throw4xxStatusFatalError(
            GET_OFFENDER_NAME,
            subjectAccessRequest,
            mapOf("subjectId" to subjectId),
          ),
        )
        .bodyToMono(String::class.java)
        .retryWhen(
          webClientRetriesSpec.retry5xxAndClientRequestErrors(
            GET_OFFENDER_NAME,
            subjectAccessRequest,
            mapOf("subjectId" to subjectId),
          ),
        )
        .block()

      val details = JSONParser(response).parseObject()
      "${details["lastName"].toString().uppercase()}, ${
        details["firstName"].toString().lowercase()
          .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
      }"
    } catch (ex: ClientAuthorizationException) {
      throw FatalSubjectAccessRequestException(
        message = "prisonApiClient error authorization exception",
        cause = ex,
        event = ACQUIRE_AUTH_TOKEN,
        subjectAccessRequestId = subjectAccessRequest.id,
        params = mapOf(
          "cause" to ex.cause?.message,
        ),
      )
    }
  }
}
