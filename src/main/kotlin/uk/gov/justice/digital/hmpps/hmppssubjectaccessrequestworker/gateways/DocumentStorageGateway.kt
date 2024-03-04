package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.config.HmppsSubjectAccessRequestWorkerExceptionHandler
import java.util.*

@Component
class DocumentStorageGateway(
  @Autowired val hmppsAuthGateway: HmppsAuthGateway,
  @Value("\${services.document-storage.base-url}") hmppsDocumentApiUrl: String,
) {
  private val webClient: WebClient = WebClient.builder().baseUrl(hmppsDocumentApiUrl).build()
  private val log = LoggerFactory.getLogger(this::class.java)

  fun storeDocument(documentId: UUID, filePath: String): String? {
    log.info("Storing document..")
    val token = hmppsAuthGateway.getClientToken()
    log.info("File path: $filePath")
    log.info("UUID: $documentId")
    log.info("Token: $token")

    val multipartBodyBuilder = MultipartBodyBuilder()
    log.info(
      "BUILDER: " + multipartBodyBuilder.apply {
        part("file", FileSystemResource("file:$filePath"))
        part("metadata", 1)
      }.build().toString(),
    )
    try {
      val response = webClient.post().uri("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$documentId")
        .header("Authorization", "Bearer $token")
        .header("Service-Name", "DPS-Subject-Access-Requests")
        .bodyValue(
          multipartBodyBuilder.apply {
            part("file", FileSystemResource("file:$filePath"))
            part("metadata", 1)
          }.build(),
        )
        .retrieve() // Don't treat 401 responses as errors:
        .onStatus(
          { status -> status === HttpStatus.BAD_REQUEST },
          { clientResponse -> throw Exception(clientResponse.bodyToMono(HmppsSubjectAccessRequestWorkerExceptionHandler::class.java).toString()) },
        )
        .bodyToMono(String::class.java)
        .block()

      return response
    } catch (exception: Exception) {
      log.info("ERROR: $exception")
      throw Exception(exception)
    }
  }

  fun retrieveDocument(documentId: UUID): JSONObject? {
    val token = hmppsAuthGateway.getClientToken()
    val response = webClient.get().uri("/documents/" + { documentId.toString() }).header("Authorization", "Bearer $token").retrieve().bodyToMono(JSONObject::class.java).block()
    return response
  }
}
