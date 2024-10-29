package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.time.StopWatch
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_SAR_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@Component
class GenericHmppsApiGateway(
  val authGateway: HmppsAuthGateway,
  val telemetryClient: TelemetryClient,
  val webClientRetriesSpec: WebClientRetriesSpec,
) {

  private val webClient = WebClient
    .builder()
    .codecs { configurer ->
      configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)
    }.build()

  companion object {
    private const val SAR_API_PATH = "/subject-access-request"
    private const val PRN_PARAM = "prn"
    private const val CRN_PARAM = "crn"
    private const val FROM_DATE_PARAM = "fromDate"
    private const val TO_DATE_PARAM = "toDate"
  }

  fun getSarData(
    serviceUrl: String,
    prn: String? = null,
    crn: String? = null,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    subjectAccessRequest: SubjectAccessRequest? = null,
  ): Map<*, *>? {
    val clientToken = getAuthToken(subjectAccessRequest?.id, serviceUrl)

    val stopWatch = StopWatch.createStarted()
    telemetryClient.dataRequestStarted(subjectAccessRequest, serviceUrl)

    val responseEntity = try {
      getDataFromService(serviceUrl, prn, crn, dateFrom, dateTo, subjectAccessRequest, clientToken)
    } catch (ex: Exception) {
      telemetryClient.dataRequestException(subjectAccessRequest, serviceUrl, stopWatch, ex)
      throw ex
    }
    stopWatch.stop()

    val responseStatus = responseEntity?.statusCode?.value()?.toString() ?: "Unknown"
    val responseBody = responseEntity?.body

    if (responseBody != null) {
      telemetryClient.dataRequestComplete(subjectAccessRequest, serviceUrl, stopWatch, responseBody, responseStatus)
    } else {
      telemetryClient.dataRequestCompleteNoDate(subjectAccessRequest, serviceUrl, stopWatch, responseStatus)
    }
    return responseBody
  }

  fun getDataFromService(
    serviceUrl: String,
    prn: String? = null,
    crn: String? = null,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    subjectAccessRequest: SubjectAccessRequest? = null,
    clientToken: String,
  ): ResponseEntity<Map<*, *>>? = webClient
    .get()
    .uri(serviceUrl) { builder ->
      builder
        .path(SAR_API_PATH)
        .queryParamIfPresent(PRN_PARAM, Optional.ofNullable(prn))
        .queryParamIfPresent(CRN_PARAM, Optional.ofNullable(crn))
        .queryParam(FROM_DATE_PARAM, dateFrom)
        .queryParam(TO_DATE_PARAM, dateTo)
        .build()
    }
    .header("Authorization", "Bearer $clientToken")
    .retrieve()
    .onStatus(
      webClientRetriesSpec.is4xxStatus(),
      webClientRetriesSpec.throw4xxStatusFatalError(
        GET_SAR_DATA,
        subjectAccessRequest?.id,
      ),
    )
    .toEntity(Map::class.java)
    .retryWhen(
      webClientRetriesSpec.retry5xxAndClientRequestErrors(
        GET_SAR_DATA,
        subjectAccessRequest?.id,
        mapOf(
          "uri" to serviceUrl,
        ),
      ),
    ).block()

  fun getAuthToken(subjectAccessRequestId: UUID?, serviceUrl: String): String {
    try {
      return authGateway.getClientToken()
    } catch (ex: Exception) {
      throw SubjectAccessRequestException(
        message = "failed to obtain client auth token",
        cause = ex,
        event = GET_SAR_DATA,
        subjectAccessRequestId = subjectAccessRequestId,
        mapOf("serviceUrl" to serviceUrl),
      )
    }
  }

  fun TelemetryClient.dataRequestStarted(subjectAccessRequest: SubjectAccessRequest?, serviceUrl: String) {
    telemetryClient.trackSarEvent(
      "ServiceDataRequestStarted",
      subjectAccessRequest,
      "serviceURL" to serviceUrl,
    )
  }

  fun TelemetryClient.dataRequestException(
    subjectAccessRequest: SubjectAccessRequest?,
    serviceUrl: String,
    stopWatch: StopWatch,
    ex: Exception,
  ) {
    telemetryClient.trackSarEvent(
      "ServiceDataRequestException",
      subjectAccessRequest,
      "serviceURL" to serviceUrl,
      "eventTime" to stopWatch.time.toString(),
      "responseSize" to "0",
      "responseStatus" to "Exception",
      "errorMessage" to (ex.message ?: "unknown"),
    )
  }

  fun TelemetryClient.dataRequestComplete(
    subjectAccessRequest: SubjectAccessRequest?,
    serviceUrl: String,
    stopWatch: StopWatch,
    responseBody: Map<*, *>,
    responseStatus: String,
  ) {
    telemetryClient.trackSarEvent(
      "ServiceDataRequestComplete",
      subjectAccessRequest,
      "serviceURL" to serviceUrl,
      "eventTime" to stopWatch.time.toString(),
      "responseSize" to responseBody.size.toString(),
      "responseStatus" to responseStatus,
    )
  }

  fun TelemetryClient.dataRequestCompleteNoDate(
    subjectAccessRequest: SubjectAccessRequest?,
    serviceUrl: String,
    stopWatch: StopWatch,
    responseStatus: String,
  ) {
    telemetryClient.trackSarEvent(
      "ServiceDataRequestNoData",
      subjectAccessRequest,
      "serviceURL" to serviceUrl,
      "eventTime" to stopWatch.time.toString(),
      "responseSize" to "0",
      "responseStatus" to responseStatus,
    )
  }
}
