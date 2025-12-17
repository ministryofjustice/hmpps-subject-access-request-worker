package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.HTML_RENDERER_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.NON_RETRYABLE_CLIENT_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.HtmlRendererTemplateException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestDocumentStoreConflictException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.HTML_RENDERER_TEMPLATE_EMPTY
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.HTML_RENDERER_TEMPLATE_HASH_MISMATCH
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.HTML_RENDERER_TEMPLATE_NOT_FOUND
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.net.URI
import java.time.Duration
import java.util.function.BiFunction
import java.util.function.Predicate

@Component
class WebClientRetriesSpec(
  webClientConfiguration: WebClientConfiguration,
  private val webClientErrorResponseService: WebClientErrorResponseService,
  val telemetryClient: TelemetryClient,
) {

  val maxRetries: Long = webClientConfiguration.maxRetries
  val backOff: Duration = webClientConfiguration.getBackoffDuration()
  protected val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

  companion object {
    private val LOG = LoggerFactory.getLogger(WebClientRetriesSpec::class.java)
  }

  fun is5xxOrClientRequestError(error: Throwable): Boolean = error is WebClientResponseException &&
    error.statusCode.is5xxServerError ||
    error is WebClientRequestException

  fun is4xxStatus(): Predicate<HttpStatusCode> = Predicate<HttpStatusCode> { code: HttpStatusCode ->
    code.is4xxClientError
  }

  fun is409Conflict(): Predicate<HttpStatusCode> = Predicate<HttpStatusCode> { code: HttpStatusCode ->
    HttpStatus.CONFLICT.isSameCodeAs(code)
  }

  fun retry5xxAndClientRequestErrors(
    event: ProcessingEvent,
    subjectAccessRequest: SubjectAccessRequest? = null,
    errorCodePrefix: ErrorCodePrefix,
    params: Map<String, Any>? = null,
  ): RetryBackoffSpec = Retry
    .backoff(maxRetries, backOff)
    .filter { err ->
      is5xxOrClientRequestError(err)
    }
    .doBeforeRetry { signal ->
      logClientRetryEvent(signal, subjectAccessRequest, event, errorCodePrefix, params)
    }
    .onRetryExhaustedThrow(
      get5xxExhaustedHandler(subjectAccessRequest, event, errorCodePrefix, params),
    )

  fun get5xxExhaustedHandler(
    subjectAccessRequest: SubjectAccessRequest?,
    event: ProcessingEvent,
    errorCodePrefix: ErrorCodePrefix,
    params: Map<String, Any>? = null,
  ) = BiFunction<RetryBackoffSpec, RetrySignal, Throwable> { _, signal ->
    telemetryClient.clientRetriesExhausted(subjectAccessRequest, signal, event)

    val errorCode = webClientErrorResponseService.getErrorCodeOrDefault(signal, errorCodePrefix)

    SubjectAccessRequestRetryExhaustedException(
      retryAttempts = signal.totalRetries(),
      cause = signal.failure(),
      event = event,
      errorCode = errorCode,
      subjectAccessRequest = subjectAccessRequest,
      params = params,
    )
  }

  fun retryHtmlRenderer5xxAndClientRequestErrors(
    subjectAccessRequest: SubjectAccessRequest? = null,
    serviceConfiguration: ServiceConfiguration,
  ): RetryBackoffSpec = Retry
    .backoff(maxRetries, backOff)
    .filter { err -> is5xxOrClientRequestError(err) }
    .doBeforeRetry { signal ->
      val params = mapOf("serviceName" to serviceConfiguration.serviceName)

      logClientRetryEvent(
        signal,
        subjectAccessRequest,
        HTML_RENDERER_REQUEST,
        ErrorCodePrefix.SAR_HTML_RENDERER,
        params,
      )
    }.onRetryExhaustedThrow { _, signal ->
      telemetryClient.clientRetriesExhausted(
        subjectAccessRequest,
        signal,
        HTML_RENDERER_REQUEST,
      )

      val errorCode = webClientErrorResponseService.getErrorCodeOrDefault(
        signal,
        ErrorCodePrefix.SAR_HTML_RENDERER,
      )

      val params = mapOf(
        "serviceName" to serviceConfiguration.serviceName,
        "serviceUrl" to serviceConfiguration.url,
      )

      when (errorCode) {
        HTML_RENDERER_TEMPLATE_HASH_MISMATCH, HTML_RENDERER_TEMPLATE_EMPTY, HTML_RENDERER_TEMPLATE_NOT_FOUND ->
          HtmlRendererTemplateException(
            signal.totalRetries(),
            errorCode,
            subjectAccessRequest,
            serviceConfiguration,
          )

        else -> SubjectAccessRequestRetryExhaustedException(
          retryAttempts = signal.totalRetries(),
          cause = signal.failure(),
          event = HTML_RENDERER_REQUEST,
          errorCode = errorCode,
          subjectAccessRequest = subjectAccessRequest,
          params = params,
        )
      }
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
        errorCode = ErrorCode(errorCodePrefix, response.statusCode().value().toString()),
        subjectAccessRequest = subjectAccessRequest,
        params = moddedParams,
      ),
    )
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

  fun TelemetryClient.clientRetriesExhausted(
    subjectAccessRequest: SubjectAccessRequest?,
    signal: RetrySignal,
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
    val errorResponse = webClientErrorResponseService.getBodyAsErrorResponseOrNull(signal)
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

  private fun Map<String, Any>.formatted(): String = this.entries.joinToString(",") { "${it.key}=${it.value}" }
}
