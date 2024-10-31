package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import org.apache.tomcat.util.json.JSONParser
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.Locale

@Service
class PrisonApiClient(
  private val prisonApiWebClient: WebClient,
) {

  fun getOffenderName(subjectId: String): String {
    return try {
      val response = prisonApiWebClient
        .get()
        .uri("/api/offenders/$subjectId")
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      val details = JSONParser(response).parseObject()
      "${details.get("lastName").toString().uppercase()}, ${
        details.get("firstName").toString().lowercase()
          .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
      }"
    } catch (exception: WebClientRequestException) {
      throw RuntimeException("Connection to ${exception.uri.authority} failed.")
    } catch (exception: WebClientResponseException.ServiceUnavailable) {
      throw RuntimeException("${exception.request?.uri?.authority} is unavailable.")
    } catch (exception: WebClientResponseException.Unauthorized) {
      throw RuntimeException("Invalid credentials used.")
    }
  }
}
