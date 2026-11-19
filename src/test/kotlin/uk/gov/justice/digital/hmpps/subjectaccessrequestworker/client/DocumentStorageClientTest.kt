package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting.InternalServiceSlackAlertService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientErrorResponseService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class DocumentStorageClientTest {
  private val alerts: InternalServiceSlackAlertService = mock()
  private val request = SubjectAccessRequest()
  private val attempts = AtomicInteger()

  @TempDir
  lateinit var directory: Path

  @Test
  fun `should alert once after upload retries are exhausted`() {
    val client = client { ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build() }

    assertThrows<SubjectAccessRequestRetryExhaustedException> { client.storeDocument(request, Files.createFile(directory.resolve("report.pdf"))) }

    assertThat(attempts.get()).isEqualTo(2)
    verify(alerts).sendServiceCallFailedAlert("Document API", "POST /documents/SUBJECT_ACCESS_REQUEST_REPORT/{id}", request.id, null)
  }

  @Test
  fun `should not alert when a retry succeeds`() {
    val client = client { attempt ->
      if (attempt == 1) {
        ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()
      } else {
        ClientResponse.create(HttpStatus.OK)
          .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
          .body("""{"fileSize":1}""")
          .build()
      }
    }

    val file = Files.write(directory.resolve("report.pdf"), byteArrayOf(1))
    assertThat(client.storeDocument(request, file).fileSize).isEqualTo(1L)
    assertThat(attempts.get()).isEqualTo(2)
    verifyNoInteractions(alerts)
  }

  private fun client(response: (Int) -> ClientResponse): DocumentStorageClient {
    val config: WebClientConfiguration = mock()
    whenever(config.maxRetries).thenReturn(1)
    whenever(config.getBackoffDuration()).thenReturn(Duration.ofMillis(1))
    val telemetry: TelemetryClient = mock()
    val retries = WebClientRetriesSpec(config, WebClientErrorResponseService(), telemetry)
    val webClient = WebClient.builder().baseUrl("http://document-api")
      .exchangeFunction { Mono.just(response(attempts.incrementAndGet())) }.build()
    return DocumentStorageClient(webClient, retries, telemetry, alerts)
  }
}
