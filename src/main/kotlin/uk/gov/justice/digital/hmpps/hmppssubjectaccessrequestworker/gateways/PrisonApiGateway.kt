package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import org.apache.tomcat.util.json.JSONParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.*

@Component
class PrisonApiGateway(
  @Autowired val hmppsAuthGateway: HmppsAuthGateway,
  @Value("\${services.prison-api.base-url}") prisonApiUrl: String
) {
  private val webClient: WebClient = WebClient.builder().baseUrl(prisonApiUrl).build()
  private val log = LoggerFactory.getLogger(this::class.java)

  fun getOffenderName(subjectId: String): String {
    try {
      val token = hmppsAuthGateway.getClientToken()
      val response = webClient
        .get()
        .uri("/api/offenders/${subjectId}")
        .header("Authorization", "Bearer $token")
        .retrieve()
        .bodyToMono(String::class.java)
        .block()

      val details = JSONParser(response).parseObject()
      return "${details.get("firstName")} ${details.get("middleName")} ${details.get("lastName")}"

    } catch (exception: WebClientRequestException) {
      throw RuntimeException("Connection to ${exception.uri.authority} failed.")
    } catch (exception: WebClientResponseException.ServiceUnavailable) {
      throw RuntimeException("${exception.request?.uri?.authority} is unavailable.")
    } catch (exception: WebClientResponseException.Unauthorized) {
      throw RuntimeException("Invalid credentials used.")
    }
  }
}
