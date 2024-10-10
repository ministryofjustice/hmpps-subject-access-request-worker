package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.Duration

@Service
class SubjectAccessRequestGateway(
  val hmppsAuthGateway: HmppsAuthGateway,
  val webClientConfig: WebClientConfiguration
) {

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
          .backoff(webClientConfig.maxRetries, webClientConfig.getBackoffDuration())
          .filter { error -> isRetryableError(error).also {
            log.info("request failed with error: ${error.message} will attempt retry? $it, back-off: ${webClientConfig.getBackoffDuration()}")
          }}
          .onRetryExhaustedThrow { _, signal ->
            log.info("request retry attempts (${signal.totalRetriesInARow()}) exhausted, cause: ${signal.failure().message} ")
            signal.failure()
          }
      ).block()
  }

  fun claim(client: WebClient, chosenSAR: SubjectAccessRequest): HttpStatusCode? {
    val token = this.getClientTokenFromHmppsAuth()
    val patchResponse = client
      .patch()
      .uri("/api/subjectAccessRequests/${chosenSAR.id}/claim")
      .header("Authorization", "Bearer $token")
      .retrieve()
    return patchResponse.toBodilessEntity().block()?.statusCode
  }

  fun complete(client: WebClient, chosenSAR: SubjectAccessRequest): HttpStatusCode? {
    val token = this.getClientTokenFromHmppsAuth()
    val patchResponse = client.patch().uri("/api/subjectAccessRequests/" + chosenSAR.id.toString() + "/complete").header("Authorization", "Bearer $token").retrieve()
    return patchResponse.toBodilessEntity().block()?.statusCode
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
