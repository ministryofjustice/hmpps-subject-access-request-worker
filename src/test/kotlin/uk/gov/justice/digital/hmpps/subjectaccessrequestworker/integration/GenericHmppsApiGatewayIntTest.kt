package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GET_SAR_DATA
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.GenericHmppsApiGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.HmppsAuthGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ComplexityOfNeedsApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ComplexityOfNeedsApiExtension.Companion.complexityOfNeedsMockApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ComplexityOfNeedsMockServer.GetSubjectAccessRequestParams
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.util.UUID

@ExtendWith(ComplexityOfNeedsApiExtension::class)
class GenericHmppsApiGatewayIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var webClientConfiguration: WebClientConfiguration

  @Mock
  private lateinit var authGatewayMock: HmppsAuthGateway

  @Mock
  private lateinit var telemetryClientMock: TelemetryClient

  @Mock
  private lateinit var subjectAccessRequestMock: SubjectAccessRequest

  @Captor
  private lateinit var appInsightsEventNameCaptor: ArgumentCaptor<String>

  @Captor
  private lateinit var appInsightsPropertiesCaptor: ArgumentCaptor<MutableMap<String, String>>

  @Captor
  private lateinit var appInsightsMetricsCaptor: ArgumentCaptor<MutableMap<String, Double>>

  private lateinit var genericApiGateway: GenericHmppsApiGateway

  private var serviceUrl = complexityOfNeedsMockApi.baseUrl()
  private var dateFrom = LocalDate.now().minusDays(1)
  private var dateTo = LocalDate.now()
  private var subjectAccessRequestId = UUID.randomUUID()
  private var sarCaseReferenceNumber = UUID.randomUUID().toString()

  private val subjectAccessRequestParams = GetSubjectAccessRequestParams(
    prn = PRN,
    crn = CRN,
    dateFrom = dateFrom,
    dateTo = dateTo,
    authToken = AUTH_TOKEN,
  )

  companion object {
    const val AUTH_TOKEN = "some-auth-token"
    const val PRN = "some-prn"
    const val CRN = "some-crn"
  }

  @BeforeEach
  fun setup() {
    whenever(authGatewayMock.getClientToken())
      .thenReturn(AUTH_TOKEN)

    whenever(subjectAccessRequestMock.id)
      .thenReturn(subjectAccessRequestId)

    whenever(subjectAccessRequestMock.sarCaseReferenceNumber)
      .thenReturn(sarCaseReferenceNumber)

    genericApiGateway = GenericHmppsApiGateway(authGatewayMock, telemetryClientMock, webClientConfiguration)
  }

  @Test
  fun `get subject access request data success`() {
    complexityOfNeedsMockApi.stubSubjectAccessRequestSuccessResponse(subjectAccessRequestParams)

    val actual = genericApiGateway.getSarData(
      serviceUrl = serviceUrl,
      prn = PRN,
      crn = CRN,
      dateFrom = dateFrom,
      dateTo = dateTo,
      subjectAccessRequest = subjectAccessRequestMock,
    )

    assertSuccessResponseBody(actual)

    verify(authGatewayMock, times(1)).getClientToken()
    complexityOfNeedsMockApi.verifyGetSubjectAccessRequestSuccessIsCalled(1, subjectAccessRequestParams)
    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent()
    assertAppInsightsRequestCompleteEvent()
  }

  @Test
  fun `get subject access request data errors getting client auth token`() {
    val error = RuntimeException("client auth token error")

    whenever(authGatewayMock.getClientToken())
      .thenThrow(error)

    val actual = assertThrows<SubjectAccessRequestException> {
      genericApiGateway.getSarData(
        serviceUrl = serviceUrl,
        prn = PRN,
        crn = CRN,
        dateFrom = dateFrom,
        dateTo = dateTo,
        subjectAccessRequest = subjectAccessRequestMock,
      )
    }

    assertExpectedErrorMessage(
      actual = actual,
      prefix = "failed to obtain client auth token,",
      "event" to GET_SAR_DATA,
      "id" to subjectAccessRequestId,
      "serviceUrl" to serviceUrl,
    )

    assertThat(actual.cause).isEqualTo(error)

    complexityOfNeedsMockApi.verifyZeroInteractions()
    verifyAppInsightsTrackEventIsNeverCalled()
  }

  @Test
  fun `get subject access request data does not retry on 4xx error`() {
    complexityOfNeedsMockApi.stubSubjectAccessRequestErrorResponse(400, subjectAccessRequestParams)

    val actual = assertThrows<FatalSubjectAccessRequestException> {
      genericApiGateway.getSarData(
        serviceUrl = serviceUrl,
        prn = PRN,
        crn = CRN,
        dateFrom = dateFrom,
        dateTo = dateTo,
        subjectAccessRequest = subjectAccessRequestMock,
      )
    }

    assertExpectedErrorMessage(
      actual = actual,
      prefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status,",
      "event" to GET_SAR_DATA,
      "id" to subjectAccessRequestId,
      "uri" to "$serviceUrl/subject-access-request",
      "httpStatus" to HttpStatus.BAD_REQUEST,
    )

    complexityOfNeedsMockApi.verifyGetSubjectAccessRequestSuccessIsCalled(1, subjectAccessRequestParams)
    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent()
    assertAppInsightsRequestExceptionEvent()
  }

  @Test
  fun `get subject access request data errors with 5xx error on initial request and retry attempts`() {
    complexityOfNeedsMockApi.stubSubjectAccessRequestErrorResponse(500, subjectAccessRequestParams)

    val actual = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      genericApiGateway.getSarData(
        serviceUrl = serviceUrl,
        prn = PRN,
        crn = CRN,
        dateFrom = dateFrom,
        dateTo = dateTo,
        subjectAccessRequest = subjectAccessRequestMock,
      )
    }

    assertExpectedErrorMessage(
      actual = actual,
      prefix = "subjectAccessRequest failed and max retry attempts (2) exhausted,",
      "event" to GET_SAR_DATA,
      "id" to subjectAccessRequestId,
      "uri" to serviceUrl,
    )

    assertThat(actual.cause).isInstanceOf(InternalServerError::class.java)

    complexityOfNeedsMockApi.verifyGetSubjectAccessRequestSuccessIsCalled(3, subjectAccessRequestParams)
    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent()
    assertAppInsightsRequestExceptionEvent()
  }

  @Test
  fun `get subject access request data errors with 5xx on initial request and succeeds on retry`() {
    complexityOfNeedsMockApi.stubSubjectAccessRequestErrorWith5xxOnInitialRequestSucceedOnRetry(
      subjectAccessRequestParams,
    )

    val actual = genericApiGateway.getSarData(
      serviceUrl = serviceUrl,
      prn = PRN,
      crn = CRN,
      dateFrom = dateFrom,
      dateTo = dateTo,
      subjectAccessRequest = subjectAccessRequestMock,
    )

    assertSuccessResponseBody(actual)

    verify(authGatewayMock, times(1)).getClientToken()
    complexityOfNeedsMockApi.verifyGetSubjectAccessRequestSuccessIsCalled(2, subjectAccessRequestParams)
    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent()
    assertAppInsightsRequestCompleteEvent()
  }

  @Test
  fun `get subject access request data retries on connection refused error`() {
    val randomUrl = "http://localhost:12345"

    val actual = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      genericApiGateway.getSarData(
        serviceUrl = randomUrl,
        prn = PRN,
        crn = CRN,
        dateFrom = dateFrom,
        dateTo = dateTo,
        subjectAccessRequest = subjectAccessRequestMock,
      )
    }

    assertExpectedErrorMessage(
      actual = actual,
      prefix = "subjectAccessRequest failed and max retry attempts (2) exhausted,",
      "event" to GET_SAR_DATA,
      "id" to subjectAccessRequestId,
      "uri" to randomUrl,
    )
    assertThat(actual.cause).isInstanceOf(WebClientRequestException::class.java)
    assertThat(actual.cause!!.message).contains("Connection refused")

    complexityOfNeedsMockApi.verifyZeroInteractions()
    verifyAppInsightsTrackEventIsCalled(2)
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
    assertThat(appInsightsEventNameCaptor.allValues[0]).isEqualTo("ServiceDataRequestStarted")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
    assertThat(appInsightsPropertiesCaptor.allValues[0]).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "sarId" to sarCaseReferenceNumber,
        "UUID" to subjectAccessRequestId.toString(),
        "serviceURL" to randomUrl,
      ),
    )
    assertAppInsightsRequestExceptionEvent()
  }

  @Test
  fun `get subject access request error connection reset by peer`() {
    complexityOfNeedsMockApi.stubSubjectAccessRequestFault(subjectAccessRequestParams)

    val actual = assertThrows<SubjectAccessRequestException> {
      genericApiGateway.getSarData(
        serviceUrl = serviceUrl,
        prn = PRN,
        crn = CRN,
        dateFrom = dateFrom,
        dateTo = dateTo,
        subjectAccessRequest = subjectAccessRequestMock,
      )
    }

    assertExpectedErrorMessage(
      actual = actual,
      prefix = "subjectAccessRequest failed and max retry attempts (2) exhausted,",
      "event" to GET_SAR_DATA,
      "id" to subjectAccessRequestId,
      "uri" to serviceUrl,
    )
    assertThat(actual.cause).isInstanceOf(WebClientRequestException::class.java)

    verify(authGatewayMock, times(1)).getClientToken()
    complexityOfNeedsMockApi.verifyGetSubjectAccessRequestSuccessIsCalled(3, subjectAccessRequestParams)
    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent()
    assertAppInsightsRequestExceptionEvent()
  }

  @Test
  fun `get subject access request data returns status 200 with no body`() {
    complexityOfNeedsMockApi.stubSubjectAccessRequestSuccessNoBody(subjectAccessRequestParams)

    val actual = genericApiGateway.getSarData(
      serviceUrl = serviceUrl,
      prn = PRN,
      crn = CRN,
      dateFrom = dateFrom,
      dateTo = dateTo,
      subjectAccessRequest = subjectAccessRequestMock,
    )

    assertThat(actual).isNull()

    complexityOfNeedsMockApi.verifyGetSubjectAccessRequestSuccessIsCalled(1, subjectAccessRequestParams)
    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent()
    assertAppInsightsRequestNoDataEvent()
  }

  fun verifyAppInsightsTrackEventIsCalled(times: Int) {
    verify(telemetryClientMock, times(times))
      .trackEvent(
        appInsightsEventNameCaptor.capture(),
        appInsightsPropertiesCaptor.capture(),
        appInsightsMetricsCaptor.capture(),
      )
  }

  private fun verifyAppInsightsTrackEventIsNeverCalled() {
    verify(telemetryClientMock, never())
      .trackEvent(any(), any(), any())
  }

  private fun assertAppInsightsRequestStartedEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
    assertThat(appInsightsEventNameCaptor.allValues[0]).isEqualTo("ServiceDataRequestStarted")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
    assertThat(appInsightsPropertiesCaptor.allValues[0]).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "sarId" to sarCaseReferenceNumber,
        "UUID" to subjectAccessRequestId.toString(),
        "serviceURL" to complexityOfNeedsMockApi.baseUrl(),
      ),
    )
  }

  private fun assertAppInsightsRequestCompleteEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestComplete")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsPropertiesCaptor.allValues[1])
      .containsOnlyKeys("sarId", "UUID", "serviceURL", "eventTime", "responseSize", "responseStatus")
  }

  private fun assertAppInsightsRequestExceptionEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestException")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsPropertiesCaptor.allValues[1])
      .containsOnlyKeys("sarId", "UUID", "serviceURL", "eventTime", "responseSize", "responseStatus", "errorMessage")
  }

  private fun assertAppInsightsRequestNoDataEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestNoData")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsPropertiesCaptor.allValues[1])
      .containsOnlyKeys("sarId", "UUID", "serviceURL", "eventTime", "responseSize", "responseStatus")
  }

  private fun assertSuccessResponseBody(actual: Map<*, *>?) {
    assertThat(actual).isNotNull
    assertThat(actual).isInstanceOf(Map::class.java)

    val body = actual as Map<*, *>
    assertThat(body["content"]).isNotNull

    val content = body["content"] as Map<*, *>
    assertThat(content["additionalProp1"]).isNotNull

    val additionalProp1 = content["additionalProp1"] as Map<*, *>
    assertThat(additionalProp1["field1"]).isEqualTo("value1")
  }

  private fun assertExpectedErrorMessage(actual: Throwable, prefix: String, vararg params: Pair<String, *>) {
    val formattedParams = params.joinToString(", ") { entry ->
      "${entry.first}=${entry.second}"
    }
    val expected = "$prefix $formattedParams"
    assertThat(actual.message).isEqualTo(expected)
  }
}
