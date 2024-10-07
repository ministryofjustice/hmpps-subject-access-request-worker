package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.*

@Component
class ProbationApiGateway(
  @Autowired val hmppsAuthGateway: HmppsAuthGateway,
  @Value("\${services.probation-api.base-url}") probationApiUrl: String,
) {
  private val webClient: WebClient = WebClient.builder().baseUrl(probationApiUrl).build()

  fun getOffenderName(subjectId: String): String {
    return try {
      val token = hmppsAuthGateway.getClientToken()
      val response = webClient
        .get()
        .uri("/probation-case/$subjectId")
        .header("Authorization", "Bearer $token")
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
