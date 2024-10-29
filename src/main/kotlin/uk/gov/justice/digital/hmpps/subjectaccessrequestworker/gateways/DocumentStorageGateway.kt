package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.io.ByteArrayOutputStream
import java.util.UUID

@Component
class DocumentStorageGateway(
  @Autowired val hmppsAuthGateway: HmppsAuthGateway,
  @Value("\${services.document-storage.base-url}") hmppsDocumentApiUrl: String,
  val webClientRetriesSpec: WebClientRetriesSpec,
) {
  private val webClient: WebClient = WebClient.builder().baseUrl(hmppsDocumentApiUrl).build()
  private val log = LoggerFactory.getLogger(this::class.java)

  fun storeDocument(subjectAccessRequestId: UUID, docBody: ByteArrayOutputStream): String? {
    log.info("Storing document with UUID $subjectAccessRequestId")
    val token = hmppsAuthGateway.getClientToken()
    val multipartBodyBuilder = MultipartBodyBuilder()
    val contentsAsResource: ByteArrayResource = object : ByteArrayResource(docBody.toByteArray()) {
      override fun getFilename(): String {
        return "report.pdf"
      }
    }
    val response = webClient.post().uri("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId")
      .header("Authorization", "Bearer $token")
      .header("Service-Name", "DPS-Subject-Access-Requests")
      .bodyValue(
        multipartBodyBuilder.apply {
          part("file", contentsAsResource)
          part("metadata", 1)
        }.build(),
      )
      .retrieve()
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(STORE_DOCUMENT, subjectAccessRequestId),
      )
      .bodyToMono(String::class.java)
      .block()
    return response
  }
}
