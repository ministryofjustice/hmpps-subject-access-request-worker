package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.time.StopWatch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
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
    val responseEntity = try {
      webClient
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
        .toEntity(Map::class.java)
        .block()
    } catch (e: Exception) {
        telemetryClient.trackEvent(
          "ServiceDataRequestException",
          mapOf(
            "sarId" to subjectAccessRequest?.sarCaseReferenceNumber.toString(),
            "UUID" to subjectAccessRequest?.id.toString(),
            "serviceURL" to serviceUrl.toString(),
            "eventTime" to stopWatch.time.toString(),
            "responseSize" to "0",
            "responseStatus" to "Exception",
            "errorMessage" to e.message.toString()
          ),
        )
        throw e
      }
    stopWatch.stop()

    val responseStatus = responseEntity?.statusCode?.value()?.toString() ?: "Unknown"
    val responseBody = responseEntity?.body

    if (responseBody != null) {
      telemetryClient.trackEvent(
        "ServiceDataRequestComplete",
        mapOf(
          "sarId" to subjectAccessRequest?.sarCaseReferenceNumber.toString(),
          "UUID" to subjectAccessRequest?.id.toString(),
          "serviceURL" to serviceUrl.toString(),
          "eventTime" to stopWatch.time.toString(),
          "responseSize" to responseBody.size.toString(),
          "responseStatus" to responseStatus,
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
          "responseStatus" to responseStatus,
        ),
      )
    }
    return responseBody
  }
}
