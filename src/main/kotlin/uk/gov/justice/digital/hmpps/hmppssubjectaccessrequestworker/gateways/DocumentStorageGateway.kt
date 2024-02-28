package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import org.json.JSONObject
import org.slf4j.LoggerFactory
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
  private val log = LoggerFactory.getLogger(this::class.java)

  fun storeDocument(documentId: Int, documentBody: String, uuid: String? = UUID.randomUUID().toString()): String? {
    log.info("Storing document..")
    val token = hmppsAuthGateway.getClientToken()
    try {
      val response = webClient.post().uri("/documents/SUBJECT_ACCESS_REQUEST_REPORT" + { uuid })
        .header("Authorization", "Bearer $token")
        .header("Service-Name", "DPS-Subject-Access-Requests")
        .bodyValue(documentBody)
        .retrieve().bodyToMono(String::class.java).block()
      return response
    } catch (exception: Exception) {
      log.info("ERROR: $exception")
      throw Exception(exception)
    }
    // return documentId.toString() + uuid
  }

  fun retrieveDocument(documentId: UUID): JSONObject? {
    val token = hmppsAuthGateway.getClientToken()
    val response = webClient.get().uri("/documents/" + { documentId.toString() }).header("Authorization", "Bearer $token").retrieve().bodyToMono(JSONObject::class.java).block()
    return response
  }
}
