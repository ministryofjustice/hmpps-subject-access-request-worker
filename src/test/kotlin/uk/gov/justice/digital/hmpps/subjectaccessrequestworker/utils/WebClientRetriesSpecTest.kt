package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.capture
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.retry.Retry.RetrySignal
import reactor.util.retry.RetryBackoffSpec
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.net.URI
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class WebClientRetriesSpecTest {

  private val objectMapper = ObjectMapper()

  private val webClientConfiguration: WebClientConfiguration = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val subjectAccessRequest: SubjectAccessRequest = mock()
  private val retryBackoffSpec: RetryBackoffSpec = mock()
  private val headers: HttpHeaders = mock()
  private val signal: RetrySignal = mock()
  private val failure: WebClientResponseException = mock()
  private val webClientErrorResponseService: WebClientErrorResponseService = WebClientErrorResponseService()

  @Captor
  private lateinit var eventNameCaptor: ArgumentCaptor<String>

  @Captor
  private lateinit var eventPropertiesCaptor: ArgumentCaptor<Map<String, String>>

  private val errorResponseJson = objectMapper.writeValueAsString(
    ErrorResponse(
      status = 400,
      errorCode = "666",
      developerMessage = "some error happened",
    ),
  )

  private val webClientRetriesSpec: WebClientRetriesSpec = WebClientRetriesSpec(
    webClientConfiguration,
    webClientErrorResponseService,
    telemetryClient,
  )

  private val sar = SubjectAccessRequest(
    sarCaseReferenceNumber = "TEST123",
    contextId = UUID.randomUUID(),
  )

  @BeforeEach
  fun setup() {
    whenever(signal.failure())
      .thenReturn(failure)

    whenever(signal.totalRetries())
      .thenReturn(2)

    whenever(failure.headers)
      .thenReturn(headers)
  }

  @Test
  fun `should generated the expected retry exhausted exception when the response body is a valid ErrorResponse object`() {
    whenever(headers.contentType)
      .thenReturn(MediaType.APPLICATION_JSON)

    whenever(failure.getResponseBodyAs(String::class.java))
      .thenReturn(errorResponseJson)

    val actual = webClientRetriesSpec.get5xxExhaustedHandler(
      subjectAccessRequest,
      ProcessingEvent.GET_LOCATION,
      ErrorCodePrefix.LOCATION_API,
    ).apply(retryBackoffSpec, signal)

    assertSubjectAccessRequestRetryExhaustedException(
      actual = actual,
      expectedRetryAttempts = 2,
      expectedCause = failure,
      expectedEvent = ProcessingEvent.GET_LOCATION,
      expectedErrorCode = ErrorCode(
        ErrorCodePrefix.LOCATION_API,
        "666",
      ),
    )
  }

  @Test
  fun `should log the expected app insight events when handling retry exhausted error`() {
    whenever(headers.contentType)
      .thenReturn(MediaType.APPLICATION_JSON)

    whenever(failure.getResponseBodyAs(String::class.java))
      .thenReturn(errorResponseJson)

    webClientRetriesSpec.get5xxExhaustedHandler(
      sar,
      ProcessingEvent.STORE_DOCUMENT,
      ErrorCodePrefix.DOCUMENT_STORE,
    ).apply(retryBackoffSpec, signal)

    verify(telemetryClient, times(1))
      .trackEvent(capture(eventNameCaptor), capture(eventPropertiesCaptor), isNull())

    assertThat(eventNameCaptor.allValues).hasSize(1)
    assertThat(eventNameCaptor.firstValue).isEqualTo("STORE_DOCUMENT_RETRIES_EXHAUSTED")

    assertThat(eventPropertiesCaptor.allValues).hasSize(1)
    assertThat(eventPropertiesCaptor.firstValue).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "sarId" to sar.sarCaseReferenceNumber,
        "UUID" to sar.id.toString(),
        "contextId" to sar.contextId.toString(),
        "retryAttempts" to "2",
        "cause" to "null",
        "totalRetries" to "2",
      ),
    )
  }

  @Test
  fun `should log expected app insights event for non retryable error`() {
    val someId = UUID.randomUUID().toString()
    val params = mapOf("someId" to someId)
    val response: ClientResponse = mock()
    val request: HttpRequest = mock()

    whenever(response.request()).thenReturn(request)
    whenever(request.uri).thenReturn(URI.create("http://localhost/some/service"))
    whenever(response.statusCode()).thenReturn(HttpStatus.UNAUTHORIZED)

    webClientRetriesSpec.throw4xxStatusFatalError(
      ProcessingEvent.RENDER_TEMPLATE,
      sar,
      ErrorCodePrefix.SAR_HTML_RENDERER,
      params,
    ).invoke(response)

    verify(telemetryClient, times(1))
      .trackEvent(capture(eventNameCaptor), capture(eventPropertiesCaptor), isNull())

    assertThat(eventNameCaptor.allValues).hasSize(1)
    assertThat(eventNameCaptor.firstValue).isEqualTo("RENDER_TEMPLATE_NON_RETRYABLE_ERROR")

    assertThat(eventPropertiesCaptor.allValues).hasSize(1)
    assertThat(eventPropertiesCaptor.firstValue).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "sarId" to sar.sarCaseReferenceNumber,
        "UUID" to sar.id.toString(),
        "contextId" to sar.contextId.toString(),
        "someId" to someId,
        "uri" to "http://localhost/some/service",
        "httpStatus" to HttpStatus.UNAUTHORIZED.toString(),
      ),
    )
  }

  @Test
  fun `should generated the expected retry exhausted exception when response body empty JSON`() {
    whenever(headers.contentType)
      .thenReturn(MediaType.APPLICATION_JSON)

    whenever(failure.statusCode)
      .thenReturn(HttpStatus.BAD_REQUEST)

    whenever(failure.getResponseBodyAs(String::class.java))
      .thenReturn("{}")

    val actual = webClientRetriesSpec.get5xxExhaustedHandler(
      subjectAccessRequest,
      ProcessingEvent.HTML_RENDERER_REQUEST,
      ErrorCodePrefix.SAR_HTML_RENDERER,
    ).apply(retryBackoffSpec, signal)

    assertSubjectAccessRequestRetryExhaustedException(
      actual = actual,
      expectedRetryAttempts = 2,
      expectedCause = failure,
      expectedEvent = ProcessingEvent.HTML_RENDERER_REQUEST,
      expectedErrorCode = ErrorCode(
        ErrorCodePrefix.SAR_HTML_RENDERER,
        HttpStatus.BAD_REQUEST.value().toString(),
      ),
    )
  }

  @Test
  fun `should generated the expected retry exhausted exception when response body is not JSON`() {
    whenever(headers.contentType)
      .thenReturn(MediaType.TEXT_PLAIN)

    whenever(failure.statusCode)
      .thenReturn(HttpStatus.INTERNAL_SERVER_ERROR)

    whenever(failure.getResponseBodyAs(String::class.java))
      .thenReturn("hello world")

    val actual = webClientRetriesSpec.get5xxExhaustedHandler(
      subjectAccessRequest,
      ProcessingEvent.STORE_DOCUMENT,
      ErrorCodePrefix.DOCUMENT_STORE,
    ).apply(retryBackoffSpec, signal)

    assertSubjectAccessRequestRetryExhaustedException(
      actual = actual,
      expectedRetryAttempts = 2,
      expectedCause = failure,
      expectedEvent = ProcessingEvent.STORE_DOCUMENT,
      expectedErrorCode = ErrorCode(
        ErrorCodePrefix.DOCUMENT_STORE,
        HttpStatus.INTERNAL_SERVER_ERROR.value().toString(),
      ),
    )
  }

  private fun assertSubjectAccessRequestRetryExhaustedException(
    actual: Throwable,
    expectedRetryAttempts: Long,
    expectedCause: Throwable?,
    expectedEvent: ProcessingEvent,
    expectedErrorCode: ErrorCode,
  ) {
    assertThat(actual).isNotNull()
    assertThat(actual).isInstanceOf(SubjectAccessRequestRetryExhaustedException::class.java)

    val retryExhaustedException = actual as SubjectAccessRequestRetryExhaustedException
    assertThat(retryExhaustedException.retryAttempts).isEqualTo(expectedRetryAttempts)
    assertThat(retryExhaustedException.cause).isEqualTo(expectedCause)
    assertThat(retryExhaustedException.event).isEqualTo(expectedEvent)
    assertThat(retryExhaustedException.errorCode).isEqualTo(expectedErrorCode)
  }
}
