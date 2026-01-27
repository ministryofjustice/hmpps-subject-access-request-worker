package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.CONVERT_WORD_DOCUMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec

@Component
class GotenbergApiClient(
  @param:Qualifier("gotenbergWebClient") val gotenbergClient: WebClient,
  private val webClientRetriesSpec: WebClientRetriesSpec,
) {
  fun convertWordDocToPdf(content: ByteArray, filename: String): ByteArray {
    val body = MultipartBodyBuilder()
    body.part("files", content)
      .header("Content-Disposition", "form-data; name=files; filename=$filename")
      .contentType(MediaType.APPLICATION_OCTET_STREAM)

    return gotenbergClient
      .post()
      .uri("/forms/libreoffice/convert")
      .contentType(MediaType.MULTIPART_FORM_DATA)
      .bodyValue(body.build())
      .retrieve()
      .onStatus(
        webClientRetriesSpec.is4xxStatus(),
        webClientRetriesSpec.throw4xxStatusFatalError(
          event = CONVERT_WORD_DOCUMENT,
          errorCodePrefix = ErrorCodePrefix.GOTENBERG_API,
          params = mapOf("filename" to filename),
        ),
      )
      .bodyToMono(ByteArray::class.java)
      .retryWhen(
        webClientRetriesSpec.retry5xxAndClientRequestErrors(
          event = CONVERT_WORD_DOCUMENT,
          params = mapOf("filename" to filename),
          errorCodePrefix = ErrorCodePrefix.GOTENBERG_API,
        ),
      )
      .block()!!
  }
}
