package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.HmppsAuthGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest

@Service
class WebClientService {

  fun getClient(url: String): WebClient {
    return WebClient.create(url)
  }
  fun getToken(): String {
    return HmppsAuthGateway("http://localhost:3000", "hmpps-integration-api-client", "clientsecret").getClientToken()
  }

  fun getUnclaimedSars(client: WebClient, token: String): Array<SubjectAccessRequest>? {
    return client.get().uri("/api/subjectAccessRequests?unclaimed=true").header("Authorization", "Bearer $token").retrieve().bodyToMono(Array<SubjectAccessRequest>::class.java).block()
  }

  fun claim(client: WebClient, chosenSAR: SubjectAccessRequest, token: String): HttpStatusCode? {
    print("Claiming...")
    val patchResponse = client.patch().uri("/api/subjectAccessRequests/" + chosenSAR.id.toString() + "/claim").header("Authorization", "Bearer $token").retrieve()
    return patchResponse.toBodilessEntity().block()?.statusCode
  }

  fun complete(client: WebClient, chosenSAR: SubjectAccessRequest, token: String): HttpStatusCode? {
    val patchResponse = client.patch().uri("/api/subjectAccessRequests/" + chosenSAR.id.toString() + "/complete").header("Authorization", "Bearer $token").retrieve()
    return patchResponse.toBodilessEntity().block()?.statusCode
  }
}
