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
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
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

    genericApiGateway = GenericHmppsApiGateway(authGatewayMock, telemetryClientMock)
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
    whenever(authGatewayMock.getClientToken())
      .thenThrow(RuntimeException::class.java)

    assertThrows<RuntimeException> {
      genericApiGateway.getSarData(
        serviceUrl = serviceUrl,
        prn = PRN,
        crn = CRN,
        dateFrom = dateFrom,
        dateTo = dateTo,
        subjectAccessRequest = subjectAccessRequestMock,
      )
    }

    complexityOfNeedsMockApi.verifyZeroInteractions()
    verifyAppInsightsTrackEventIsNeverCalled()
  }

  @Test
  fun `get subject access request data errors with 4xx error`() {
    complexityOfNeedsMockApi.stubSubjectAccessRequestErrorResponse(400, subjectAccessRequestParams)

    val actual = assertThrows<WebClientResponseException> {
      genericApiGateway.getSarData(
        serviceUrl = serviceUrl,
        prn = PRN,
        crn = CRN,
        dateFrom = dateFrom,
        dateTo = dateTo,
        subjectAccessRequest = subjectAccessRequestMock,
      )
    }

    assertThat(actual).isInstanceOf(BadRequest::class.java)

    complexityOfNeedsMockApi.verifyGetSubjectAccessRequestSuccessIsCalled(1, subjectAccessRequestParams)
    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent()
    assertAppInsightsRequestExceptionEvent()
  }

  @Test
  fun `get subject access request data errors with 5xx error`() {
    complexityOfNeedsMockApi.stubSubjectAccessRequestErrorResponse(500, subjectAccessRequestParams)

    val actual = assertThrows<WebClientResponseException> {
      genericApiGateway.getSarData(
        serviceUrl = serviceUrl,
        prn = PRN,
        crn = CRN,
        dateFrom = dateFrom,
        dateTo = dateTo,
        subjectAccessRequest = subjectAccessRequestMock,
      )
    }

    assertThat(actual).isInstanceOf(InternalServerError::class.java)

    complexityOfNeedsMockApi.verifyGetSubjectAccessRequestSuccessIsCalled(1, subjectAccessRequestParams)
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

  fun verifyAppInsightsTrackEventIsNeverCalled() {
    verify(telemetryClientMock, never())
      .trackEvent(any(), any(), any())
  }

  fun assertAppInsightsRequestStartedEvent() {
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

  fun assertAppInsightsRequestCompleteEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestComplete")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsPropertiesCaptor.allValues[1])
      .containsOnlyKeys("sarId", "UUID", "serviceURL", "eventTime", "responseSize", "responseStatus")
  }

  fun assertAppInsightsRequestExceptionEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestException")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsPropertiesCaptor.allValues[1])
      .containsOnlyKeys("sarId", "UUID", "serviceURL", "eventTime", "responseSize", "responseStatus", "errorMessage")
  }

  fun assertAppInsightsRequestNoDataEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestNoData")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsPropertiesCaptor.allValues[1])
      .containsOnlyKeys("sarId", "UUID", "serviceURL", "eventTime", "responseSize", "responseStatus")
  }

  fun assertSuccessResponseBody(actual: Map<*, *>?) {
    assertThat(actual).isNotNull
    assertThat(actual).isInstanceOf(Map::class.java)

    val body = actual as Map<*, *>
    assertThat(body["content"]).isNotNull

    val content = body["content"] as Map<*, *>
    assertThat(content["additionalProp1"]).isNotNull

    val additionalProp1 = content["additionalProp1"] as Map<*, *>
    assertThat(additionalProp1["field1"]).isEqualTo("value1")
  }
}
