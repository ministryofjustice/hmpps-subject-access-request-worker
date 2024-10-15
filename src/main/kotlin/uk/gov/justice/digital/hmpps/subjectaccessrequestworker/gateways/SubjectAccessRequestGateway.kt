package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.Duration
import kotlin.jvm.Throws

@Service
class SubjectAccessRequestGateway(
  val hmppsAuthGateway: HmppsAuthGateway,
  webClientConfig: WebClientConfiguration,
) {

  private val backoff: Duration = webClientConfig.getBackoffDuration()
  private val maxRetries: Long = webClientConfig.maxRetries

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

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
      .bodyToMono(Array<SubjectAccessRequest>::class.java)
      .retryWhen(
        Retry
          .backoff(maxRetries, backoff)
          .filter { error ->
            isRetryableError(error).also {
              log.info("request failed with error: ${error.message} will attempt retry? $it, back-off: $backoff")
            }
          }
          .onRetryExhaustedThrow { _, signal ->
            log.info("request retry attempts (${signal.totalRetriesInARow()}) exhausted, cause: ${signal.failure().message} ")
            signal.failure()
          },
      ).block()
  }

  /**
   * Claims a subject access request. Will attempt retry on 5xx status errors, will not attempt retry on 4xx errors.
   */
  @Throws(SubjectAccessRequestException::class)
  fun claim(client: WebClient, subjectAccessRequest: SubjectAccessRequest) {
    val token = this.getClientTokenFromHmppsAuth()

    client
      .patch()
      .uri("/api/subjectAccessRequests/${subjectAccessRequest.id}/claim")
      .header("Authorization", "Bearer $token")
      .retrieve()
      .onStatus(
        { code: HttpStatusCode -> code.is4xxClientError },
        { response ->
          Mono.error(
            SubjectAccessRequestException.claimRequestFailedException(
              subjectAccessRequest.id,
              response.statusCode(),
            ),
          )
        },
      )
      .toBodilessEntity()
      .retryWhen(
        Retry
          .backoff(maxRetries, backoff)
          .filter { error -> error is WebClientResponseException && error.statusCode.is5xxServerError }
          .onRetryExhaustedThrow { _, signal ->
            SubjectAccessRequestException.claimRequestRetryExhaustedException(
              subjectAccessRequest.id,
              signal.failure(),
              signal.totalRetries(),
            )
          },
      )
      .block()
  }

  fun complete(client: WebClient, subjectAccessRequest: SubjectAccessRequest): HttpStatusCode? {
    val token = this.getClientTokenFromHmppsAuth()
    return client
      .patch()
      .uri("/api/subjectAccessRequests/${subjectAccessRequest.id}/complete")
      .header("Authorization", "Bearer $token")
      .retrieve()
      .toBodilessEntity()
      .block()?.statusCode
  }

  private fun isRetryableError(t: Throwable): Boolean {
    return when (t) {
      is WebClientResponseException -> {
        return t.statusCode.is5xxServerError
      }
      is WebClientRequestException -> true
      else -> false
    }
  }
}
