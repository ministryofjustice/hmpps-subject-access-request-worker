package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class GenericHmppsApiGateway(@Autowired val hmppsAuthGateway: HmppsAuthGateway) {
  fun getSarData(serviceUrl: String?, prn: String?, crn: String?, dateFrom: String?, dateTo: String?): String {
    val clientToken = hmppsAuthGateway.getClientToken()

    val webClient: WebClient = WebClient.builder().baseUrl(serviceUrl).build()

    val response = webClient
      .get()
      .uri("/subject-access-request")
      .header("Authorization", "Bearer $clientToken")
      .retrieve()
      .bodyToMono(String::class.java)
      .block()

    return response
  }
}
