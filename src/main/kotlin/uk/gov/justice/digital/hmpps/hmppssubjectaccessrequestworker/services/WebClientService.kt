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
  fun getUnclaimedSars(client: WebClient): Array<SubjectAccessRequest>? {
    val token = HmppsAuthGateway("http://localhost:9090", "hmpps-integration-api-client", "clientsecret").getClientToken()
    return client.get().uri("/api/subjectAccessRequests?unclaimed=true").header("Authorization", "Bearer $token").retrieve().bodyToMono(Array<SubjectAccessRequest>::class.java).block()
  }

  fun claim(client: WebClient, chosenSAR: SubjectAccessRequest): HttpStatusCode? {
    val patchResponse = client.patch().uri("/users/{id}" + chosenSAR.id.toString()).retrieve() // .bodyToMono(String::class.java)
    return patchResponse.toBodilessEntity().block()?.statusCode
  }

  fun complete(client: WebClient, chosenSAR: SubjectAccessRequest): HttpStatusCode? {
    val patchResponse = client.patch().uri("/users/{id}/claim").retrieve()
    return patchResponse.toBodilessEntity().block()?.statusCode
  }
}
