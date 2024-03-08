package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate

@Component
class GenericHmppsApiGateway(@Autowired val hmppsAuthGateway: HmppsAuthGateway) {
  fun getSarData(serviceUrl: String, prn: String? = null, crn: String? = null, dateFrom: LocalDate? = null, dateTo: LocalDate? = null): Map<*, *>? {
    val clientToken = hmppsAuthGateway.getClientToken()
    val webClient: WebClient = WebClient.builder().baseUrl(serviceUrl).build()
    val response = webClient
      .get()
      .uri { builder ->
        builder.path("/subject-access-request")
          .queryParam("prn", prn)
          .queryParam("crn", crn)
          .queryParam("fromDate", dateFrom)
          .queryParam("toDate", dateTo)
          .build()
      }
      .header("Authorization", "Bearer $clientToken")
      .retrieve()
      .bodyToMono(Map::class.java)
      .block()
    return response
  }
}
