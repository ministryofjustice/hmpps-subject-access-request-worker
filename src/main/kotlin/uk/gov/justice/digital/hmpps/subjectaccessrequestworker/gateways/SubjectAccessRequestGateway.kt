package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.CLAIM_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.COMPLETE_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_UNCLAIMED_REQUESTS
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec

@Service
class SubjectAccessRequestGateway(
  val hmppsAuthGateway: HmppsAuthGateway,
  val webClientRetriesSpec: WebClientRetriesSpec,
) {

  fun getClient(url: String): WebClient {
    return WebClient.create(url) // TODO what other config does this need?
  }

  fun getClientTokenFromHmppsAuth(): String = hmppsAuthGateway.getClientToken()

  fun getUnclaimed(client: WebClient): Array<SubjectAccessRequest>? {
    val token = this.getClientTokenFromHmppsAuth()

    return client
      .get()
      .uri("/api/subjectAccessRequests?unclaimed=true")
      .header("Authorization", "Bearer $token")
      .retrieve()
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(event = GET_UNCLAIMED_REQUESTS),
      )
      .bodyToMono(Array<SubjectAccessRequest>::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(event = GET_UNCLAIMED_REQUESTS),
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
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(CLAIM_REQUEST, subjectAccessRequest),
      )
      .toBodilessEntity()
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(CLAIM_REQUEST, subjectAccessRequest),
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
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(COMPLETE_REQUEST, subjectAccessRequest),
      )
      .toBodilessEntity()
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(COMPLETE_REQUEST, subjectAccessRequest),
      ).block()
  }
}
