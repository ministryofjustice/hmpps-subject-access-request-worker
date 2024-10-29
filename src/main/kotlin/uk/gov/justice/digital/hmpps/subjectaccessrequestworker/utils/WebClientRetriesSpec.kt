package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse

import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import reactor.util.retry.RetryBackoffSpec
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import java.time.Duration
import java.util.UUID
import java.util.function.Predicate

@Component
class WebClientRetriesSpec(webClientConfiguration: WebClientConfiguration) {

  private val maxRetries: Long = webClientConfiguration.maxRetries
  private val backOff: Duration = webClientConfiguration.getBackoffDuration()

  companion object {
    private val LOG = LoggerFactory.getLogger(WebClientRetriesSpec::class.java)
  }

  fun retry5xxAndClientRequestErrors(event: ProcessingEvent, subjectAccessRequestId: UUID?, params: Map<String, Any>?):
    RetryBackoffSpec {
    return Retry
      .backoff(maxRetries, backOff)
      .filter { err -> is5xxOrClientRequestError(err) }
      .doBeforeRetry { signal ->
        LOG.error("subject access request $event failed with error=${signal.failure()}, attempting retry after backoff: $backOff, ${params?.formatted()}")
      }
      .onRetryExhaustedThrow { _, signal ->
        SubjectAccessRequestRetryExhaustedException(
          retryAttempts = signal.totalRetries(),
          cause = signal.failure(),
          event = event,
          subjectAccessRequestId = subjectAccessRequestId,
          params = params,
        )
      }
  }

  fun is5xxOrClientRequestError(error: Throwable): Boolean =
    error is WebClientResponseException && error.statusCode.is5xxServerError || error is WebClientRequestException

  fun is4xxStatus(): Predicate<HttpStatusCode> =
    Predicate<HttpStatusCode> { code: HttpStatusCode -> code.is4xxClientError }

  fun throw4xxStatusFatalError(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    params: Map<String, Any>? = null,
  ) =
    { response: ClientResponse ->
      val moddedParams = params?.toMutableMap() ?: mutableMapOf<String, Any>().also { map ->
        map.putIfAbsent("uri", response.request().uri)
        map.putIfAbsent("httpStatus", response.statusCode())
      }

      Mono.error<SubjectAccessRequestException>(
        FatalSubjectAccessRequestException(
          message = "client 4xx response status",
          event = event,
          subjectAccessRequestId = subjectAccessRequestId,
          params = moddedParams,
        ),
      )
    }

  private fun Map<String, Any>.formatted(): String {
    return "${this.entries.joinToString(",") { "${it.key}=${it.value}" }}"
  }

}