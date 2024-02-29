package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import java.io.File
import java.util.*

@Component
class DocumentStorageGateway(
  @Autowired val hmppsAuthGateway: HmppsAuthGateway,
  @Value("\${services.document-storage.base-url}") hmppsDocumentApiUrl: String,
) {
  private val webClient: WebClient = WebClient.builder().baseUrl(hmppsDocumentApiUrl).build()
  private val log = LoggerFactory.getLogger(this::class.java)

  fun storeDocument(documentId: Int, filePath: String, uuid: String?): String? {
    val uuidForPath: String
    if (uuid == null) {
      uuidForPath = UUID.randomUUID().toString()
    } else {
      uuidForPath = uuid
    }
    log.info("Storing document..")
    val token = hmppsAuthGateway.getClientToken()
    log.info("File path: $filePath")
    log.info("UUID: $uuidForPath")
    log.info("Token: $token")

    val uploadFile = File(filePath)

    val multipartBodyBuilder = MultipartBodyBuilder()
    multipartBodyBuilder.part("file", FileSystemResource(uploadFile))
    multipartBodyBuilder.part("metadata", 1)
    log.info(multipartBodyBuilder.build().toSingleValueMap().keys.toString())
    log.info(multipartBodyBuilder.build().toSingleValueMap().values.toString())
    try {
      val response = webClient.post().uri("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$uuidForPath")
        .header("Authorization", "Bearer $token")
        .header("Service-Name", "DPS-Subject-Access-Requests")
        .bodyValue(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
        .retrieve().onStatus(HttpStatusCode::is4xxClientError) { response ->
          log.info(response.bodyToMono(String::class.java).toString())
          throw Exception(response.bodyToMono(String::class.java).toString()) }
        .bodyToMono(String::class.java).block()
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
