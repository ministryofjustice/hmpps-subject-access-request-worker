package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting.InternalServiceSlackAlertService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientErrorResponseService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class HtmlRendererApiClientTest {
  private val alerts: InternalServiceSlackAlertService = mock()
  private val request = SubjectAccessRequest()
  private val service = ServiceConfiguration(
    serviceName = "test-service",
    label = "Test",
    url = "http://test-service",
    enabled = true,
    templateMigrated = true,
    category = ServiceCategory.PRISON,
  )

  private fun client(response: () -> Mono<ClientResponse>): HtmlRendererApiClient {
    val config: WebClientConfiguration = mock()
    whenever(config.maxRetries).thenReturn(1)
    whenever(config.getBackoffDuration()).thenReturn(Duration.ofMillis(1))
    val retries = WebClientRetriesSpec(config, WebClientErrorResponseService(), mock<TelemetryClient>())
    val webClient = WebClient.builder().baseUrl("http://renderer")
      .exchangeFunction { response() }.build()
    return HtmlRendererApiClient(webClient, retries, alerts)
  }

  private fun response(status: HttpStatus) = Mono.just(
    ClientResponse.create(status).header("Content-Type", "application/json")
      .body("""{"documentKey":"document","templateVersion":"1"}""").build(),
  )

  @Test
  fun `should alert once after renderer retries are exhausted and preserve processing exception`() {
    val calls = AtomicInteger()
    val client = client {
      calls.incrementAndGet()
      Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())
    }

    assertThrows<SubjectAccessRequestRetryExhaustedException> { client.submitRenderRequest(request, service) }

    assertThat(calls.get()).isEqualTo(2)
    verify(alerts).sendServiceCallFailedAlert("SAR HTML renderer", "POST /subject-access-request/render", request.id, service.serviceName)
  }

  @Test
  fun `should not alert when renderer succeeds after a retry`() {
    val calls = AtomicInteger()
    val client = client {
      if (calls.incrementAndGet() == 1) {
        Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())
      } else {
        response(HttpStatus.OK)
      }
    }

    assertThat(client.submitRenderRequest(request, service).documentKey).isEqualTo("document")
    assertThat(calls.get()).isEqualTo(2)
    verifyNoInteractions(alerts)
  }

  @Test
  fun `should alert immediately for non retryable renderer response`() {
    val client = client { Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST).build()) }

    assertThrows<FatalSubjectAccessRequestException> { client.submitRenderRequest(request, service) }

    verify(alerts).sendServiceCallFailedAlert("SAR HTML renderer", "POST /subject-access-request/render", request.id, service.serviceName)
  }

  @Test
  fun `should preserve authorization exception mapping and alert HAAR`() {
    val error = ClientAuthorizationException(OAuth2Error("invalid_token_response"), "sar-client")
    val client = client { Mono.error(error) }

    val actual = assertThrows<FatalSubjectAccessRequestException> { client.submitRenderRequest(request, service) }

    assertThat(actual.errorCode).isEqualTo(ErrorCode.HTML_RENDERER_AUTH_ERROR)
    assertThat(actual.cause).isSameAs(error)
    verify(alerts).sendServiceCallFailedAlert("SAR HTML renderer", "POST /subject-access-request/render", request.id, service.serviceName)
  }

  @Test
  fun `should alert HAAR when subject data summary call fails`() {
    val client = client { Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()) }

    assertThrows<Exception> {
      client.getServiceSummary(HtmlRendererApiClient.SubjectDataHeldRequest(serviceName = "test-service"))
    }

    verify(alerts).sendServiceCallFailedAlert(
      "SAR HTML renderer",
      "POST /subject-access-request/subject-data-held-summary",
      null,
      "test-service",
    )
  }
}
