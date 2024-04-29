package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.time.StopWatch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.config.trackEvent
import java.time.LocalDate
import java.util.Optional

@Component
class GenericHmppsApiGateway(
  @Autowired val hmppsAuthGateway: HmppsAuthGateway,
  val telemetryClient: TelemetryClient,
) {
  fun getSarData(serviceUrl: String, prn: String? = null, crn: String? = null, dateFrom: LocalDate? = null, dateTo: LocalDate? = null): Map<*, *>? {
    val clientToken = hmppsAuthGateway.getClientToken()
    val webClient: WebClient = WebClient.builder().baseUrl(serviceUrl).build()
    val stopWatch = StopWatch.createStarted()
    val response = webClient
      .get()
      .uri { builder ->
        builder.path("/subject-access-request")
          .queryParamIfPresent("prn", Optional.ofNullable(prn))
          .queryParamIfPresent("crn", Optional.ofNullable(crn))
          .queryParam("fromDate", dateFrom)
          .queryParam("toDate", dateTo)
          .build()
      }
      .header("Authorization", "Bearer $clientToken")
      .retrieve()
      .bodyToMono(Map::class.java)
      .block()
    stopWatch.stop()
    if (response != null) {
      telemetryClient.trackEvent(
        "ServiceResponse",
        mapOf(
          "url" to serviceUrl,
          "responseTime" to stopWatch.time.toString(),
          "responseSize" to response.size.toString(),
        ),
      )
    }
    return response
  }
}
