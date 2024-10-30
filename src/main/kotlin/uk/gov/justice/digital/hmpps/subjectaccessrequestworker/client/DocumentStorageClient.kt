package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.STORE_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.io.ByteArrayOutputStream
import java.util.UUID

@Service
class DocumentStorageClient(
  private val documentStorageWebClient: WebClient,
  val webClientRetriesSpec: WebClientRetriesSpec,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun storeDocument(subjectAccessRequestId: UUID, docBody: ByteArrayOutputStream): String? {
    log.info("Storing document with UUID $subjectAccessRequestId")
    val multipartBodyBuilder = MultipartBodyBuilder()
    val contentsAsResource: ByteArrayResource = object : ByteArrayResource(docBody.toByteArray()) {
      override fun getFilename(): String {
        return "report.pdf"
      }
    }
    return documentStorageWebClient.post().uri("/documents/SUBJECT_ACCESS_REQUEST_REPORT/$subjectAccessRequestId")
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
  }
}
