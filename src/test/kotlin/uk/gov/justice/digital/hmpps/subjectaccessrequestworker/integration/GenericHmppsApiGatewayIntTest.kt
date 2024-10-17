package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    assertAppInsightsSuccessEvents()
  }

  fun assertAppInsightsSuccessEvents() {
    verify(telemetryClientMock, times(2))
      .trackEvent(
        appInsightsEventNameCaptor.capture(),
        appInsightsPropertiesCaptor.capture(),
        appInsightsMetricsCaptor.capture(),
      )

    assertThat(appInsightsEventNameCaptor.allValues).hasSize(2)
    assertThat(appInsightsEventNameCaptor.allValues[0]).isEqualTo("ServiceDataRequestStarted")
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestComplete")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSize(2)
    assertThat(appInsightsPropertiesCaptor.allValues[0]).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "sarId" to sarCaseReferenceNumber,
        "UUID" to subjectAccessRequestId.toString(),
        "serviceURL" to complexityOfNeedsMockApi.baseUrl(),
      ),
    )

    val customProperties = appInsightsPropertiesCaptor.allValues[1]
    assertThat(customProperties)
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
