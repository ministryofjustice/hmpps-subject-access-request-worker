package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class GotenbergApiClient(@Qualifier("gotenbergWebClient") val gotenbergClient: WebClient) {
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
      .retrieve().bodyToMono(ByteArray::class.java)
      .block()!!
  }
}
