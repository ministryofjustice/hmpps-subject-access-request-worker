package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import org.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.UUID

@Component
class DocumentStorageGateway(
  @Autowired val hmppsAuthGateway: HmppsAuthGateway,
  @Value("\${services.document-storage.base-url}") hmppsDocumentApiUrl: String,
) {
  private val webClient: WebClient = WebClient.builder().baseUrl(hmppsDocumentApiUrl).build()

  fun storeDocument(documentId: Int, documentBody: String): String {
    val uuid = UUID.randomUUID()
    val token = hmppsAuthGateway.getClientToken()
    webClient.post().uri("/documents/SUBJECT_ACCESS_REQUEST_REPORT" + { documentId.toString() }).header("Authorization", "Bearer $token").retrieve().bodyToMono(String::class.java).block()
    return documentId.toString() + uuid.toString()
  }

  fun retrieveDocument(documentId: UUID): JSONObject? {
    val token = hmppsAuthGateway.getClientToken()
    val response = webClient.get().uri("/documents/" + { documentId.toString() }).header("Authorization", "Bearer $token").retrieve().bodyToMono(JSONObject::class.java).block()
    return response
  }
}
