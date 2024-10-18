package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_SAR_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.util.Optional
import java.util.function.Predicate

@Component
class GenericHmppsApiGateway(
  val authGateway: HmppsAuthGateway,
  val telemetryClient: TelemetryClient,
  webClientConfig: WebClientConfiguration,
) {

  private val webClient = WebClient
    .builder()
    .codecs { configurer ->
      configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)
    }.build()

  private val maxRetries = webClientConfig.maxRetries
  private val backOff = webClientConfig.getBackoffDuration()

  companion object {
    private const val SAR_API_PATH = "/subject-access-request"
    private const val RETRY_ERR_LOG_FMT =
      "get subject access data request failed with error, url:%s, id=%s attempting retry after backoff: %s"
    private const val PRN_PARAM = "prn"
    private const val CRN_PARAM = "crn"
    private const val FROM_DATE_PARAM = "fromDate"
    private const val TO_DATE_PARAM = "toDate"

    private val LOG = LoggerFactory.getLogger(this::class.java)
  }

  fun getSarData(
    serviceUrl: String,
    prn: String? = null,
    crn: String? = null,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    subjectAccessRequest: SubjectAccessRequest? = null,
  ): Map<*, *>? {
    val clientToken = authGateway.getClientToken()

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
      is4xxStatus(),
      handle4xxStatus(subjectAccessRequest),
    )
    .toEntity(Map::class.java)
    .retryWhen(
      customRetrySpec(subjectAccessRequest, serviceUrl),
    ).block()

  private fun is4xxStatus(): Predicate<HttpStatusCode> =
    Predicate<HttpStatusCode> { code: HttpStatusCode -> code.is4xxClientError }

  private fun handle4xxStatus(subjectAccessRequest: SubjectAccessRequest?) = { response: ClientResponse ->
    Mono.error<SubjectAccessRequestException>(
      FatalSubjectAccessRequestException(
        message = "client 4xx response status",
        event = GET_SAR_DATA,
        subjectAccessRequestId = subjectAccessRequest?.id,
        params = mapOf(
          "uri" to response.request().uri,
          "httpStatus" to response.statusCode(),
        ),
      ),
    )
  }

  private fun customRetrySpec(subjectAccessRequest: SubjectAccessRequest?, serviceUrl: String) = Retry
    .backoff(maxRetries, backOff)
    .filter { err -> isRetryableError(err) }
    .doBeforeRetry { signal ->
      LOG.error(RETRY_ERR_LOG_FMT.format(serviceUrl, subjectAccessRequest?.id, backOff), signal.failure())
    }
    .onRetryExhaustedThrow { _, signal ->
      SubjectAccessRequestRetryExhaustedException(
        retryAttempts = signal.totalRetries(),
        cause = signal.failure(),
        event = GET_SAR_DATA,
        subjectAccessRequestId = subjectAccessRequest?.id,
        params = mapOf(
          "uri" to serviceUrl,
        ),
      )
    }

  /**
   * An error is "retryable" if it's a 5xx error or a client request error. 4xx client response errors are not retried.
   */
  private fun isRetryableError(error: Throwable): Boolean =
    error is WebClientResponseException && error.statusCode.is5xxServerError || error is WebClientRequestException

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
