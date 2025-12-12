package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import reactor.util.retry.Retry.RetrySignal
import reactor.util.retry.RetryBackoffSpec
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.CLIENT_REQUEST_RETRY
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.CLIENT_RETRIES_EXHAUSTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.DOCUMENT_STORE_CONFLICT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.NON_RETRYABLE_CLIENT_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestDocumentStoreConflictException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.defaultErrorCodeFor
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.net.URI
import java.time.Duration
import java.util.function.Predicate

@Component
class WebClientRetriesSpec(
  webClientConfiguration: WebClientConfiguration,
  val telemetryClient: TelemetryClient,
) {

  private val maxRetries: Long = webClientConfiguration.maxRetries
  private val backOff: Duration = webClientConfiguration.getBackoffDuration()
  private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

  companion object {
    private val LOG = LoggerFactory.getLogger(WebClientRetriesSpec::class.java)
  }

  fun retry5xxAndClientRequestErrors(
    event: ProcessingEvent,
    subjectAccessRequest: SubjectAccessRequest? = null,
    errorCodePrefix: ErrorCodePrefix,
    params: Map<String, Any>? = null,
  ): RetryBackoffSpec = Retry
    .backoff(maxRetries, backOff)
    .filter { err -> is5xxOrClientRequestError(err) }
    .doBeforeRetry { signal -> logClientRetryEvent(signal, subjectAccessRequest, event, errorCodePrefix, params) }
    .onRetryExhaustedThrow { _, signal ->
      telemetryClient.clientRetriesExhausted(subjectAccessRequest, signal, event)

      SubjectAccessRequestRetryExhaustedException(
        retryAttempts = signal.totalRetries(),
        cause = signal.failure(),
        event = event,
        errorCode = getErrorCodeFromResponseOrDefault(signal, errorCodePrefix),
        subjectAccessRequest = subjectAccessRequest,
        params = params,
      )
    }

  private fun getErrorCodeFromResponseOrDefault(
    signal: RetrySignal,
    errorCodePrefix: ErrorCodePrefix,
  ): ErrorCode = signal.getBodyAsErrorResponseOrNull()?.errorCode.takeIf { !it.isNullOrBlank() }?.let {
    ErrorCode(it, errorCodePrefix)
  } ?: defaultErrorCodeFor(errorCodePrefix)

  private fun RetrySignal.getBodyAsErrorResponseOrNull(): ErrorResponse? = this.failure()
    .takeIf { it is WebClientResponseException }
    ?.let { ex ->
      ex as WebClientResponseException
      val bodyString = ex.getResponseBodyAs(String::class.java)

      bodyString.takeIf { responseBodyIsJson(it, ex) }
        ?.let { objectMapper.readValue(it, ErrorResponse::class.java) }
    }

  private fun responseBodyIsJson(
    body: String?,
    wcex: WebClientResponseException,
  ): Boolean = wcex.headers.contentType == MediaType.APPLICATION_JSON &&
    body != null &&
    body.startsWith("{") &&
    body.endsWith("}")

  fun is5xxOrClientRequestError(error: Throwable): Boolean = error is WebClientResponseException &&
    error.statusCode.is5xxServerError ||
    error is WebClientRequestException

  fun is4xxStatus(): Predicate<HttpStatusCode> = Predicate<HttpStatusCode> { code: HttpStatusCode ->
    code.is4xxClientError
  }

  fun throw4xxStatusFatalError(
    event: ProcessingEvent,
    subjectAccessRequest: SubjectAccessRequest? = null,
    errorCodePrefix: ErrorCodePrefix,
    params: Map<String, Any>? = null,
  ) = { response: ClientResponse ->
    val moddedParams = buildMap<String, Any> {
      params?.let { putAll(it) }
      putIfAbsent("uri", response.request().uri.toString())
      putIfAbsent("httpStatus", response.statusCode())
    }

    telemetryClient.nonRetryableClientError(subjectAccessRequest, event, params)

    Mono.error<SubjectAccessRequestException>(
      FatalSubjectAccessRequestException(
        message = "client 4xx response status",
        event = event,
        errorCode = ErrorCode(response.statusCode(), errorCodePrefix),
        subjectAccessRequest = subjectAccessRequest,
        params = moddedParams,
      ),
    )
  }

  fun is409Conflict(): Predicate<HttpStatusCode> = Predicate<HttpStatusCode> { code: HttpStatusCode ->
    HttpStatus.CONFLICT.isSameCodeAs(code)
  }

  fun throwDocumentApiConflictException(subjectAccessRequest: SubjectAccessRequest) = { response: ClientResponse ->
    telemetryClient.documentStoreConflictError(subjectAccessRequest, response.request().uri)

    Mono.error<SubjectAccessRequestException>(
      SubjectAccessRequestDocumentStoreConflictException(
        subjectAccessRequest = subjectAccessRequest,
        mapOf(
          "uri" to response.request().uri.toString(),
          "httpStatus" to response.statusCode(),
        ),
      ),
    )
  }

  private fun Map<String, Any>.formatted(): String = this.entries.joinToString(",") { "${it.key}=${it.value}" }

  fun TelemetryClient.clientRetriesExhausted(
    subjectAccessRequest: SubjectAccessRequest?,
    signal: Retry.RetrySignal,
    event: ProcessingEvent,
  ) {
    telemetryClient.trackSarEvent(
      event = CLIENT_RETRIES_EXHAUSTED,
      subjectAccessRequest = subjectAccessRequest,
      "retryAttempts" to signal.totalRetries().toString(),
      "cause" to (signal.failure().cause?.message ?: "null"),
      "event" to event.name,
      "totalRetries" to signal.totalRetries().toString(),
    )
  }

  fun TelemetryClient.nonRetryableClientError(
    subjectAccessRequest: SubjectAccessRequest?,
    event: ProcessingEvent,
    params: Map<String, Any>? = null,
  ) {
    val kvPairs = mutableListOf(
      Pair("event", event.name),
    )
    // Convert params to list of pair<String, String>
    params?.entries?.forEach { entry ->
      kvPairs.add(Pair(entry.key, entry.value.toString()))
    }

    telemetryClient.trackSarEvent(
      event = NON_RETRYABLE_CLIENT_ERROR,
      subjectAccessRequest = subjectAccessRequest,
      "event" to event.name,
      *kvPairs.toTypedArray(),
    )
  }

  fun TelemetryClient.documentStoreConflictError(
    subjectAccessRequest: SubjectAccessRequest?,
    uri: URI,
  ) {
    telemetryClient.trackSarEvent(
      event = DOCUMENT_STORE_CONFLICT,
      subjectAccessRequest = subjectAccessRequest,
      "event" to STORE_DOCUMENT.name,
      "uri" to uri.toString(),
    )
  }

  fun logClientRetryEvent(
    signal: RetrySignal,
    subjectAccessRequest: SubjectAccessRequest?,
    event: ProcessingEvent,
    errorCodePrefix: ErrorCodePrefix,
    params: Map<String, Any>? = null,
  ) {
    val errorResponse = signal.getBodyAsErrorResponseOrNull()
    val errorCode = errorResponse?.errorCode ?: "N/A"
    val errorMessage = errorResponse?.developerMessage ?: signal.failure().message ?: "N/A"

    telemetryClient.trackSarEvent(
      event = CLIENT_REQUEST_RETRY,
      subjectAccessRequest = subjectAccessRequest,
      "event" to event.name,
      "errorCode" to errorCode,
      "errorMessage" to errorMessage,
      "totalRetries" to signal.totalRetries().toString(),
    )

    LOG.error(
      "subject access request $event, id=${subjectAccessRequest?.id} failed with " +
        "error=$errorMessage, errorCode=$errorCode, attempting retry after backoff: $backOff, " +
        "${params?.formatted()}",
    )
  }
}
