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
import org.springframework.web.reactive.function.client.WebClientRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.NomisMappingApiClient.NomisLocationMapping
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestExceptionWithCauseNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client.BaseClientIntTest.Companion.StubErrorResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.NomisMappingsApiExtension.Companion.nomisMappingsApi

private const val LOCATION_NOMIS_ID = 785333

class NomisMappingsApiClientIntTest : BaseClientIntTest() {

  @Autowired
  private lateinit var nomisMappingsApiClient: NomisMappingApiClient

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
  fun `should get nomis location mapping when exists`() {
    hmppsAuth.stubGrantToken()
    nomisMappingsApi.stubLocationMapping(LOCATION_NOMIS_ID)

    val response = nomisMappingsApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)

    assertThat(response)
      .isEqualTo(NomisLocationMapping("000047c0-38e1-482e-8bbc-07d4b5f57e23", LOCATION_NOMIS_ID))
  }

  @ParameterizedTest
  @MethodSource("responseStubs4xx")
  fun `should not retry on 4xx error`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken()
    nomisMappingsApi.stubGetLocationMappingResponse(LOCATION_NOMIS_ID, stubResponse.getResponse())

    val exception = assertThrows<FatalSubjectAccessRequestException> {
      nomisMappingsApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)
    }

    nomisMappingsApi.verifyGetLocationMappingCalled(1, LOCATION_NOMIS_ID)

    assertExpectedSubjectAccessRequestExceptionWithCauseNull(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status",
      expectedEvent = ProcessingEvent.GET_LOCATION_MAPPING,
      expectedErrorCode = ErrorCode(ErrorCodePrefix.NOMIS_API, stubResponse.status.value().toString()),
      expectedParams = mapOf(
        "nomisLocationId" to LOCATION_NOMIS_ID,
        "uri" to "${nomisMappingsApi.baseUrl()}/api/locations/nomis/$LOCATION_NOMIS_ID",
        "httpStatus" to stubResponse.status,
      ),
    )
  }

  @Test
  fun `should not retry on 404 error and return null`() {
    hmppsAuth.stubGrantToken()
    nomisMappingsApi.stubGetLocationMappingResponse(LOCATION_NOMIS_ID, WireMock.notFound())

    val actual = nomisMappingsApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)

    assertThat(actual).isNull()
    nomisMappingsApi.verifyGetLocationMappingCalled(1, LOCATION_NOMIS_ID)
  }

  @ParameterizedTest
  @MethodSource("status5xxResponseStubs")
  fun `should retry on 5xx error`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken()
    nomisMappingsApi.stubGetLocationMappingResponse(LOCATION_NOMIS_ID, stubResponse.getResponse())

    val exception = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      nomisMappingsApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)
    }

    nomisMappingsApi.verifyGetLocationMappingCalled(3, LOCATION_NOMIS_ID)

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed and max retry attempts (2) exhausted",
      expectedCause = stubResponse.expectedException,
      expectedEvent = ProcessingEvent.GET_LOCATION_MAPPING,
      expectedErrorCode = ErrorCode(ErrorCodePrefix.NOMIS_API, stubResponse.status.value().toString()),
      expectedParams = mapOf(
        "nomisLocationId" to LOCATION_NOMIS_ID,
      ),
    )
  }

  @ParameterizedTest
  @MethodSource("authErrorResponseStubs")
  fun `should not retry on authentication failures`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken(stubResponse.getResponse())

    val exception = assertThrows<FatalSubjectAccessRequestException> {
      nomisMappingsApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)
    }

    nomisMappingsApi.verifyGetLocationMappingNeverCalled(LOCATION_NOMIS_ID)

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: nomisMappingsApiClient error authorization exception",
      expectedCause = stubResponse.expectedException,
      expectedEvent = ProcessingEvent.ACQUIRE_AUTH_TOKEN,
      expectedErrorCode = ErrorCode.NOMIS_API_AUTH_ERROR,
      expectedParams = null,
    )
  }
}
