package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.CLAIM_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.COMPLETE_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_UNCLAIMED_REQUESTS
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.Duration

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
        { status -> status.is4xxClientError },
        { response ->
          Mono.error(FatalSubjectAccessRequestException(GET_UNCLAIMED_REQUESTS, null, response.statusCode()))
        },
      )
      .bodyToMono(Array<SubjectAccessRequest>::class.java)
      .retryWhen(
        Retry
          .backoff(maxRetries, backoff)
          .filter { error -> isRetryableError(error) }
          .onRetryExhaustedThrow { _, signal ->
            SubjectAccessRequestRetryExhaustedException(
              GET_UNCLAIMED_REQUESTS,
              null,
              signal.failure(),
              signal.totalRetries(),
            )
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
        { code: HttpStatusCode -> code.is4xxClientError },
        { response ->
          Mono.error(
            FatalSubjectAccessRequestException(CLAIM_REQUEST, subjectAccessRequestId, response.statusCode()),
          )
        },
      )
      .toBodilessEntity()
      .retryWhen(
        Retry
          .backoff(maxRetries, backoff)
          .filter { error -> isRetryableError(error) }
          .onRetryExhaustedThrow { _, signal ->
            SubjectAccessRequestRetryExhaustedException(
              CLAIM_REQUEST,
              subjectAccessRequestId,
              signal.failure(),
              signal.totalRetries(),
            )
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
        { status -> status.is4xxClientError },
        { response ->
          Mono.error(
            FatalSubjectAccessRequestException(COMPLETE_REQUEST, subjectAccessRequestId, response.statusCode()),
          )
        },
      )
      .toBodilessEntity()
      .retryWhen(
        Retry.backoff(maxRetries, backoff)
          .filter { error -> isRetryableError(error) }
          .onRetryExhaustedThrow { _, signal ->
            SubjectAccessRequestRetryExhaustedException(
              COMPLETE_REQUEST,
              subjectAccessRequestId,
              signal.failure(),
              signal.totalRetries(),
            )
          },
      ).block()
  }

  /**
   * An error is "retryable" if it's a 5xx error or a client request error. 4xx client response errors are not retried.
   */
  private fun isRetryableError(error: Throwable): Boolean =
    error is WebClientResponseException && error.statusCode.is5xxServerError || error is WebClientRequestException
}
