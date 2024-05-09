package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest

@Service
class SubjectAccessRequestGateway(@Autowired val hmppsAuthGateway: HmppsAuthGateway) {

  fun getClient(url: String): WebClient {
    return WebClient.create(url)
  }
  fun getClientTokenFromHmppsAuth(): String {
    return hmppsAuthGateway.getClientToken()
  }

  fun getUnclaimed(client: WebClient): Array<SubjectAccessRequest>? {
    val token = this.getClientTokenFromHmppsAuth()
    return client.get().uri("/api/subjectAccessRequests?unclaimed=true").header("Authorization", "Bearer $token").retrieve().bodyToMono(Array<SubjectAccessRequest>::class.java).block()
  }

  fun claim(client: WebClient, chosenSAR: SubjectAccessRequest): HttpStatusCode? {
    val token = this.getClientTokenFromHmppsAuth()
    val patchResponse = client.patch().uri("/api/subjectAccessRequests/" + chosenSAR.id.toString() + "/claim").header("Authorization", "Bearer $token").retrieve()
    return patchResponse.toBodilessEntity().block()?.statusCode
  }

  fun complete(client: WebClient, chosenSAR: SubjectAccessRequest): HttpStatusCode? {
    val token = this.getClientTokenFromHmppsAuth()
    val patchResponse = client.patch().uri("/api/subjectAccessRequests/" + chosenSAR.id.toString() + "/complete").header("Authorization", "Bearer $token").retrieve()
    return patchResponse.toBodilessEntity().block()?.statusCode
  }
}
