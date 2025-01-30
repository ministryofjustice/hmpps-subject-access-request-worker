package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

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
import reactor.util.retry.RetryBackoffSpec
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestDocumentStoreConflictException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
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

  companion object {
    private val LOG = LoggerFactory.getLogger(WebClientRetriesSpec::class.java)
    private const val DOCUMENT_STORE_CONFLICT_EVENT = "DocumentStoreConflictError"
    private const val NON_RETRYABLE_CLIENT_ERROR_EVENT = "NonRetryableClientError"
    private const val CLIENT_RETRIES_EXHAUSTED_ERROR_EVENT = "ClientRetriesExhaustedError"
  }

  fun retry5xxAndClientRequestErrors(
    event: ProcessingEvent,
    subjectAccessRequest: SubjectAccessRequest? = null,
    params: Map<String, Any>? = null,
  ): RetryBackoffSpec = Retry
    .backoff(maxRetries, backOff)
    .filter { err -> is5xxOrClientRequestError(err) }
    .doBeforeRetry { signal ->
      LOG.error(
        "subject access request $event, id=${subjectAccessRequest?.id} failed with " +
          "error=${signal.failure()}, attempting retry after backoff: $backOff, ${params?.formatted()}",
      )
    }
    .onRetryExhaustedThrow { _, signal ->
      telemetryClient.clientRetriesExhausted(subjectAccessRequest, signal, event)

      SubjectAccessRequestRetryExhaustedException(
        retryAttempts = signal.totalRetries(),
        cause = signal.failure(),
        event = event,
        subjectAccessRequest = subjectAccessRequest,
        params = params,
      )
    }

  fun is5xxOrClientRequestError(error: Throwable): Boolean = error is WebClientResponseException && error.statusCode.is5xxServerError || error is WebClientRequestException

  fun is4xxStatus(): Predicate<HttpStatusCode> = Predicate<HttpStatusCode> { code: HttpStatusCode -> code.is4xxClientError }

  fun throw4xxStatusFatalError(
    event: ProcessingEvent,
    subjectAccessRequest: SubjectAccessRequest? = null,
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
        subjectAccessRequest = subjectAccessRequest,
        params = moddedParams,
      ),
    )
  }

  fun is409Conflict(): Predicate<HttpStatusCode> = Predicate<HttpStatusCode> { code: HttpStatusCode -> HttpStatus.CONFLICT.isSameCodeAs(code) }

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
      CLIENT_RETRIES_EXHAUSTED_ERROR_EVENT,
      subjectAccessRequest,
      "retryAttempts" to signal.totalRetries().toString(),
      "cause" to (signal.failure().cause?.message ?: "null"),
      "event" to event.name,
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
      NON_RETRYABLE_CLIENT_ERROR_EVENT,
      subjectAccessRequest,
      "event" to event.name,
      *kvPairs.toTypedArray(),
    )
  }

  fun TelemetryClient.documentStoreConflictError(
    subjectAccessRequest: SubjectAccessRequest?,
    uri: URI,
  ) {
    telemetryClient.trackSarEvent(
      DOCUMENT_STORE_CONFLICT_EVENT,
      subjectAccessRequest,
      "event" to ProcessingEvent.STORE_DOCUMENT.name,
      "uri" to uri.toString(),
    )
  }
}
