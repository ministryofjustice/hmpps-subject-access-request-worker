package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.time.StopWatch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.config.trackEvent
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.util.Optional

@Component
class GenericHmppsApiGateway(
  @Autowired val hmppsAuthGateway: HmppsAuthGateway,
  val telemetryClient: TelemetryClient,
) {
  fun getSarData(serviceUrl: String, prn: String? = null, crn: String? = null, dateFrom: LocalDate? = null, dateTo: LocalDate? = null, subjectAccessRequest: SubjectAccessRequest? = null): Map<*, *>? {
    val clientToken = hmppsAuthGateway.getClientToken()
    val webClient: WebClient = WebClient
      .builder()
      .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024) }
      .baseUrl(serviceUrl)
      .build()
    val stopWatch = StopWatch.createStarted()
    telemetryClient.trackEvent(
      "ServiceDataRequestStarted",
      mapOf(
        "sarId" to subjectAccessRequest?.sarCaseReferenceNumber.toString(),
        "UUID" to subjectAccessRequest?.id.toString(),
        "serviceURL" to serviceUrl.toString(),
      ),
    )
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
        "ServiceDataRequestComplete",
        mapOf(
          "sarId" to subjectAccessRequest?.sarCaseReferenceNumber.toString(),
          "UUID" to subjectAccessRequest?.id.toString(),
          "serviceURL" to serviceUrl.toString(),
          "eventTime" to stopWatch.time.toString(),
          "responseSize" to response.size.toString(),
        ),
      )
    } else {
      telemetryClient.trackEvent(
        "ServiceDataRequestFailed",
        mapOf(
          "sarId" to subjectAccessRequest?.sarCaseReferenceNumber.toString(),
          "UUID" to subjectAccessRequest?.id.toString(),
          "serviceURL" to serviceUrl.toString(),
          "eventTime" to stopWatch.time.toString(),
          "responseSize" to "0",
        ),
      )
    }
    return response
  }
}
