package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.web.reactive.function.client.WebClientRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.LocationsApiClient.LocationDetailsResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestExceptionWithCauseNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client.BaseClientIntTest.Companion.StubErrorResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.LocationsApiExtension.Companion.locationsApi

private const val LOCATION_DPS_ID = "00000be5-081c-4374-8214-18af310d3d4a"

class LocationsApiClientIntTest : BaseClientIntTest() {

  @Autowired
  private lateinit var locationsApiClient: LocationsApiClient

  companion object {
    @JvmStatic
    fun responseStubs4xx(): List<StubErrorResponse> = listOf(
      StubErrorResponse(HttpStatus.BAD_REQUEST, WebClientRequestException::class.java),
      StubErrorResponse(HttpStatus.UNAUTHORIZED, WebClientRequestException::class.java),
      StubErrorResponse(HttpStatus.FORBIDDEN, WebClientRequestException::class.java),
    )
  }

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling the Prison API.
    clearOauthClientCache("sar-client", "anonymousUser")
  }

  @Test
  fun `should get location details when exists`() {
    hmppsAuth.stubGrantToken()
    locationsApi.stubGetLocation(LOCATION_DPS_ID)

    val response = locationsApiClient.getLocationDetails(LOCATION_DPS_ID)

    assertThat(response).isEqualTo(LocationDetailsResponse(LOCATION_DPS_ID, "PROPERTY BOX 27", "PROP_BOXES-PB027"))
  }

  @ParameterizedTest
  @MethodSource("responseStubs4xx")
  fun `should not retry on 4xx error`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken()
    locationsApi.stubGetLocationResponse(LOCATION_DPS_ID, stubResponse.getResponse())

    val exception = assertThrows<FatalSubjectAccessRequestException> {
      locationsApiClient.getLocationDetails(LOCATION_DPS_ID)
    }

    locationsApi.verifyGetLocationCalled(1, LOCATION_DPS_ID)

    assertExpectedSubjectAccessRequestExceptionWithCauseNull(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status",
      expectedEvent = ProcessingEvent.GET_LOCATION,
      expectedParams = mapOf(
        "dpsLocationId" to LOCATION_DPS_ID,
        "uri" to "${locationsApi.baseUrl()}/locations/$LOCATION_DPS_ID",
        "httpStatus" to stubResponse.status,
      ),
    )
  }

  @Test
  fun `should not retry on 404 error and return null`() {
    hmppsAuth.stubGrantToken()
    locationsApi.stubGetLocationResponse(LOCATION_DPS_ID, WireMock.notFound())

    val actual = locationsApiClient.getLocationDetails(LOCATION_DPS_ID)

    assertThat(actual).isNull()
    locationsApi.verifyGetLocationCalled(1, LOCATION_DPS_ID)
  }

  @ParameterizedTest
  @MethodSource("status5xxResponseStubs")
  fun `should retry on 5xx error`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken()
    locationsApi.stubGetLocationResponse(LOCATION_DPS_ID, stubResponse.getResponse())

    val exception = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      locationsApiClient.getLocationDetails(LOCATION_DPS_ID)
    }

    locationsApi.verifyGetLocationCalled(3, LOCATION_DPS_ID)

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed and max retry attempts (2) exhausted",
      expectedCause = stubResponse.expectedException,
      expectedEvent = ProcessingEvent.GET_LOCATION,
      expectedParams = mapOf(
        "dpsLocationId" to LOCATION_DPS_ID,
      ),
    )
  }

  @ParameterizedTest
  @MethodSource("authErrorResponseStubs")
  fun `should not retry on authentication failures`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken(stubResponse.getResponse())

    val exception = assertThrows<FatalSubjectAccessRequestException> {
      locationsApiClient.getLocationDetails(LOCATION_DPS_ID)
    }

    locationsApi.verifyGetLocationNeverCalled(LOCATION_DPS_ID)

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: locationsApiClient error authorization exception",
      expectedCause = stubResponse.expectedException,
      expectedEvent = ProcessingEvent.ACQUIRE_AUTH_TOKEN,
      expectedParams = mapOf(
        "cause" to "$AUTH_ERROR_PREFIX ${stubResponse.status.value()} ${stubResponse.status.reasonPhrase}: [no body]",
      ),
    )
  }
}
