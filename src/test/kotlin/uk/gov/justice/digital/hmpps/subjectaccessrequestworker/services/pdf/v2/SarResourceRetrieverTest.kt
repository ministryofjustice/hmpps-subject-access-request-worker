package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_ATTACHMENT
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.S3_GET_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.URI
import java.util.UUID

class SarResourceRetrieverTest {

  private val documentStoreService: DocumentStoreService = mock()
  private val sarResourceRetriever = SarResourceRetriever(documentStoreService)
  private val documentKey = "${UUID.randomUUID()}/wiremock-test-service/image1234.jpg"
  private val url = URI("http://sar-inline-attachments/$documentKey").toURL()

  @Test
  fun `should retrieve resource from document store when url with sar inline attachment host`() = runTest {
    val expectedBytes = ByteArrayInputStream("Hello world!".toByteArray())

    whenever(documentStoreService.getInlineAttachment(documentKey))
      .thenReturn(expectedBytes)

    sarResourceRetriever.getInputStreamByUrl(url)?.use { inputStream ->
      BufferedReader(InputStreamReader(inputStream)).use { reader ->
        assertThat(reader.readText()).isEqualTo("Hello world!")
      }
    }

    verify(documentStoreService, times(1)).getInlineAttachment(documentKey)
  }

  @Test
  fun `should propagate exception thrown by documentStoreService getInlineAttachment`() = runTest {
    val expected = SubjectAccessRequestException(
      message = "failed to get inline-attachment from bucket",
      event = GET_ATTACHMENT,
      errorCode = S3_GET_ERROR,
      params = mapOf(
        "inlineAttachment" to documentKey,
      ),
    )
    whenever(documentStoreService.getInlineAttachment(documentKey))
      .thenThrow(expected)

    val actual = assertThrows<Exception> { sarResourceRetriever.getInputStreamByUrl(url) }

    assertThat(actual).isEqualTo(expected)
  }
}
