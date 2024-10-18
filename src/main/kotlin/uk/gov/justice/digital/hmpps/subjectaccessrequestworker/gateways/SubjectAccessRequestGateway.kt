package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.CLAIM_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.COMPLETE_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_UNCLAIMED_REQUESTS
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.Duration
import java.util.UUID
import java.util.function.Predicate

@Service
class SubjectAccessRequestGateway(
  val hmppsAuthGateway: HmppsAuthGateway,
  webClientConfig: WebClientConfiguration,
) {

  private val backoff: Duration = webClientConfig.getBackoffDuration()
  private val maxRetries: Long = webClientConfig.maxRetries

  fun getClient(url: String): WebClient {
    return WebClient.create(url) // TODO what other config does this need?
  }

  fun getClientTokenFromHmppsAuth(): String {
    return hmppsAuthGateway.getClientToken()
  }

  fun getUnclaimed(client: WebClient): Array<SubjectAccessRequest>? {
    val token = this.getClientTokenFromHmppsAuth()

    return client
      .get()
      .uri("/api/subjectAccessRequests?unclaimed=true")
      .header("Authorization", "Bearer $token")
      .retrieve()
      .onStatus(
        is4xxStatus(),
        handle4xxStatus(subjectAccessRequest = null, event = GET_UNCLAIMED_REQUESTS),
      )
      .bodyToMono(Array<SubjectAccessRequest>::class.java)
      .retryWhen(
        Retry
          .backoff(maxRetries, backoff)
          .filter { error -> isRetryableError(error) }
          .onRetryExhaustedThrow { _, signal ->
            retriesExhaustedException(signal, GET_UNCLAIMED_REQUESTS, null)
          },
      ).block()
  }

  /**
   * Claims a subject access request. Will attempt retry on 5xx status errors, will not attempt retry on 4xx errors.
   */
  @Throws(SubjectAccessRequestException::class)
  fun claim(client: WebClient, subjectAccessRequest: SubjectAccessRequest) {
    val token = this.getClientTokenFromHmppsAuth()
    val subjectAccessRequestId = subjectAccessRequest.id

    client
      .patch()
      .uri("/api/subjectAccessRequests/$subjectAccessRequestId/claim")
      .header("Authorization", "Bearer $token")
      .retrieve()
      .onStatus(
        is4xxStatus(),
        handle4xxStatus(subjectAccessRequest = subjectAccessRequest, event = CLAIM_REQUEST),
      )
      .toBodilessEntity()
      .retryWhen(
        Retry
          .backoff(maxRetries, backoff)
          .filter { error -> isRetryableError(error) }
          .onRetryExhaustedThrow { _, signal ->
            retriesExhaustedException(signal, CLAIM_REQUEST, subjectAccessRequestId)
          },
      ).block()
  }

  fun complete(client: WebClient, subjectAccessRequest: SubjectAccessRequest) {
    val token = this.getClientTokenFromHmppsAuth()
    val subjectAccessRequestId = subjectAccessRequest.id

    client
      .patch()
      .uri("/api/subjectAccessRequests/$subjectAccessRequestId/complete")
      .header("Authorization", "Bearer $token")
      .retrieve()
      .onStatus(
        is4xxStatus(),
        handle4xxStatus(subjectAccessRequest = subjectAccessRequest, event = COMPLETE_REQUEST),
      )
      .toBodilessEntity()
      .retryWhen(
        Retry.backoff(maxRetries, backoff)
          .filter { error -> isRetryableError(error) }
          .onRetryExhaustedThrow { _, signal ->
            retriesExhaustedException(signal, COMPLETE_REQUEST, subjectAccessRequestId)
          },
      ).block()
  }

  private fun is4xxStatus(): Predicate<HttpStatusCode> =
    Predicate<HttpStatusCode> { code: HttpStatusCode -> code.is4xxClientError }

  private fun handle4xxStatus(subjectAccessRequest: SubjectAccessRequest?, event: ProcessingEvent) =
    { response: ClientResponse ->
      Mono.error<SubjectAccessRequestException>(
        FatalSubjectAccessRequestException(
          message = "client 4xx response status",
          event = event,
          subjectAccessRequestId = subjectAccessRequest?.id,
          params = mapOf(
            "uri" to response.request().uri,
            "httpStatus" to response.statusCode(),
          ),
        ),
      )
    }

  private fun retriesExhaustedException(
    signal: Retry.RetrySignal,
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
  ) = SubjectAccessRequestRetryExhaustedException(
    retryAttempts = signal.totalRetries(),
    cause = signal.failure(),
    event = event,
    subjectAccessRequestId = subjectAccessRequestId,
  )

  /**
   * An error is "retryable" if it's a 5xx error or a client request error. 4xx client response errors are not retried.
   */
  private fun isRetryableError(error: Throwable): Boolean =
    error is WebClientResponseException && error.statusCode.is5xxServerError || error is WebClientRequestException
}
