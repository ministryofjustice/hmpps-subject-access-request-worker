package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.Locale

@Service
class ProbationApiClient(
  private val probationApiWebClient: WebClient,
) {

//  fun getOffenderName(personOnProbationId: String): PersonOnProbationDetails? = probationApiWebClient
//    .get()
//    .uri("/probation-case/$personOnProbationId")
//    .retrieve()
//    .bodyToMono(PersonOnProbationDetails::class.java)
//    .block()
//
//  @JsonIgnoreProperties(ignoreUnknown = true)
//  data class PersonOnProbationDetails(
//    val forename: String,
//    val surname: String,
//  )

  fun getOffenderName(subjectId: String): String {
    return try {
      val response = probationApiWebClient
        .get()
        .uri("/probation-case/$subjectId")
        .retrieve()
        .bodyToMono(Map::class.java)
        .block()

      val nameMap = response["name"] as Map<String, String>

      "${nameMap["surname"]?.uppercase()}, ${nameMap["forename"]?.lowercase()
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"
    } catch (exception: WebClientRequestException) {
      throw RuntimeException("Connection to ${exception.uri.authority} failed.")
    } catch (exception: WebClientResponseException.ServiceUnavailable) {
      throw RuntimeException("${exception.request?.uri?.authority} is unavailable.")
    } catch (exception: WebClientResponseException.Unauthorized) {
      throw RuntimeException("Invalid credentials used.")
    }
  }
}
