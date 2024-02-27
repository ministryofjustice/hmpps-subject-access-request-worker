package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import org.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@Component
class DocumentStorageGateway(
  @Autowired val hmppsAuthGateway: HmppsAuthGateway,
  @Value("\${services.document-storage.base-url}") hmppsDocumentApiUrl: String,
) {
  private val webClient: WebClient = WebClient.builder().baseUrl(hmppsDocumentApiUrl).build()

  fun storeDocument(documentId: Int, documentBody: String, uuid: String? = UUID.randomUUID().toString()): String {
    val token = hmppsAuthGateway.getClientToken()
    webClient.post().uri("/documents/SUBJECT_ACCESS_REQUEST_REPORT" + { uuid })
      .header("Authorization", "Bearer $token")
      .header("Service-Name", "DPS-Subject-Access-Requests")
      .bodyValue(documentBody)
      .retrieve().bodyToMono(String::class.java).block()
    return documentId.toString() + uuid
  }

  fun retrieveDocument(documentId: UUID): JSONObject? {
    val token = hmppsAuthGateway.getClientToken()
    val response = webClient.get().uri("/documents/" + { documentId.toString() }).header("Authorization", "Bearer $token").retrieve().bodyToMono(JSONObject::class.java).block()
    return response
  }
}
