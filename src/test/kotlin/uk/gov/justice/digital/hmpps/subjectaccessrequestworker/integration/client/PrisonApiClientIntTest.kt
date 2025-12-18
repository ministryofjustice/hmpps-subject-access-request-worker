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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestExceptionWithCauseNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client.BaseClientIntTest.Companion.StubErrorResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.util.UUID

class PrisonApiClientIntTest : BaseClientIntTest() {

  @Autowired
  private lateinit var prisonApiClient: PrisonApiClient

  private val subjectAccessRequest = SubjectAccessRequest(
    id = UUID.randomUUID(),
    sarCaseReferenceNumber = UUID.randomUUID().toString(),
    contextId = UUID.randomUUID(),
  )

  companion object {
    const val SUBJECT_ID = "A1234AA"

    @JvmStatic
    fun responseStubs4xx(): List<StubErrorResponse> = listOf(
      StubErrorResponse(HttpStatus.BAD_REQUEST, WebClientRequestException::class.java),
      StubErrorResponse(HttpStatus.UNAUTHORIZED, WebClientRequestException::class.java),
      StubErrorResponse(HttpStatus.FORBIDDEN, WebClientRequestException::class.java),
    )

    @JvmStatic
    fun responseStubsNamesNullAndEmpty(): List<PrisonApiClient.GetOffenderDetailsResponse> = listOf(
      PrisonApiClient.GetOffenderDetailsResponse(null, null),
      PrisonApiClient.GetOffenderDetailsResponse("", null),
      PrisonApiClient.GetOffenderDetailsResponse(null, ""),
      PrisonApiClient.GetOffenderDetailsResponse("", ""),
    )
  }

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling the Prison API.
    clearOauthClientCache("sar-client", "anonymousUser")
  }

  @Test
  fun `should get prisoner`() {
    hmppsAuth.stubGrantToken()
    prisonApi.stubGetOffenderDetails(SUBJECT_ID)

    val response = prisonApiClient.getOffenderName(subjectAccessRequest, SUBJECT_ID)

    assertThat(response).isNotNull
    assertThat(response).isEqualTo("REACHER, Joe")
  }

  @ParameterizedTest
  @MethodSource("responseStubsNamesNullAndEmpty")
  fun `should return empty when`(apiResponse: PrisonApiClient.GetOffenderDetailsResponse) {
    hmppsAuth.stubGrantToken()
    prisonApi.stubGetOffenderDetails(SUBJECT_ID, apiResponse)

    val response = prisonApiClient.getOffenderName(subjectAccessRequest, SUBJECT_ID)

    assertThat(response).isEqualTo("")
  }

  @ParameterizedTest
  @MethodSource("responseStubs4xx")
  fun `should not retry on 4xx error`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken()
    prisonApi.stubResponseFor(SUBJECT_ID, stubResponse.getResponse())

    val exception = assertThrows<FatalSubjectAccessRequestException> {
      prisonApiClient.getOffenderName(subjectAccessRequest, SUBJECT_ID)
    }

    prisonApi.verifyApiCalled(1, SUBJECT_ID)

    assertExpectedSubjectAccessRequestExceptionWithCauseNull(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status",
      expectedEvent = ProcessingEvent.GET_OFFENDER_NAME,
      expectedErrorCode = ErrorCode(ErrorCodePrefix.PRISON_API, stubResponse.status.value().toString()),
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedParams = mapOf(
        "subjectId" to SUBJECT_ID,
        "uri" to "${prisonApi.baseUrl()}/api/offenders/$SUBJECT_ID",
        "httpStatus" to stubResponse.status,
      ),
    )
  }

  @Test
  fun `should not retry on 404 error and return empty string`() {
    hmppsAuth.stubGrantToken()
    prisonApi.stubResponseFor(SUBJECT_ID, WireMock.notFound())

    val actual = prisonApiClient.getOffenderName(subjectAccessRequest, SUBJECT_ID)

    assertThat(actual).isEmpty()
    prisonApi.verifyApiCalled(1, SUBJECT_ID)
  }

  @ParameterizedTest
  @MethodSource("status5xxResponseStubs")
  fun `should retry on 5xx error`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken()
    prisonApi.stubResponseFor(SUBJECT_ID, stubResponse.getResponse())

    val exception = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      prisonApiClient.getOffenderName(subjectAccessRequest, SUBJECT_ID)
    }

    prisonApi.verifyApiCalled(3, SUBJECT_ID)

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed and max retry attempts (2) exhausted",
      expectedCause = stubResponse.expectedException,
      expectedEvent = ProcessingEvent.GET_OFFENDER_NAME,
      expectedErrorCode = ErrorCode(ErrorCodePrefix.PRISON_API, stubResponse.status.value().toString()),
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedParams = mapOf(
        "subjectId" to SUBJECT_ID,
      ),
    )
  }

  @ParameterizedTest
  @MethodSource("authErrorResponseStubs")
  fun `should not retry on authentication failures`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken(stubResponse.getResponse())

    val exception = assertThrows<FatalSubjectAccessRequestException> {
      prisonApiClient.getOffenderName(subjectAccessRequest, SUBJECT_ID)
    }

    prisonApi.verifyApiNeverCalled()

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: prisonApiClient error authorization exception",
      expectedCause = stubResponse.expectedException,
      expectedEvent = ProcessingEvent.ACQUIRE_AUTH_TOKEN,
      expectedErrorCode = ErrorCode.PRISON_API_AUTH_ERROR,
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedParams = null,
    )
  }
}
