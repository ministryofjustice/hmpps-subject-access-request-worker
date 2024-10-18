package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.CLAIM_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.COMPLETE_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.HmppsAuthGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.SubjectAccessRequestApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.SubjectAccessRequestApiExtension.Companion.subjectAccessRequestApiMock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(SubjectAccessRequestApiExtension::class)
class SubjectAccessRequestGatewayIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var sarGateway: SubjectAccessRequestGateway

  @MockBean
  private lateinit var hmppsAuthGateway: HmppsAuthGateway

  @Mock
  private lateinit var sarRequestMock: SubjectAccessRequest

  private lateinit var webClient: WebClient

  companion object {
    private const val AUTH_TOKEN = "ABC1234"
    private val subjectAccessRequestId = UUID.randomUUID()
  }

  @BeforeEach
  fun setup() {
    webClient = WebClient.builder()
      .baseUrl("http://localhost:${subjectAccessRequestApiMock.port()}")
      .build()

    whenever(hmppsAuthGateway.getClientToken())
      .thenReturn(AUTH_TOKEN)

    whenever(sarRequestMock.id)
      .thenReturn(subjectAccessRequestId)
  }

  @Nested
  inner class GetUnclaimedSubjectAccessRequestsIntTest {
    @Test
    fun `get unclaimed requests is successful`() {
      subjectAccessRequestApiMock.stubGetUnclaimedRequestsSuccess(AUTH_TOKEN)

      val result: Array<SubjectAccessRequest>? = sarGateway.getUnclaimed(webClient)

      assertGetUnclaimedSubjectAccessRequestsResponse(result)
      subjectAccessRequestApiMock.verifyGetUnclaimedSubjectAccessRequestsIsCalled(times = 1, token = AUTH_TOKEN)
    }

    @Test
    fun `get unclaimed requests fails with 5xx status on first attempt but is successful on retry`() {
      subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsOnFirstAttemptWithStatus(500, AUTH_TOKEN)

      val result: Array<SubjectAccessRequest>? = sarGateway.getUnclaimed(webClient)

      assertGetUnclaimedSubjectAccessRequestsResponse(result)
      subjectAccessRequestApiMock.verifyGetUnclaimedSubjectAccessRequestsIsCalled(times = 2, token = AUTH_TOKEN)
    }

    @Test
    fun `get unclaimed requests throws exception when all requests and retries fail with 5xx status`() {
      subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsWithStatus(500, AUTH_TOKEN)

      val actual = assertThrows<SubjectAccessRequestRetryExhaustedException> {
        sarGateway.getUnclaimed(webClient)
      }

      assertThat(actual.message).isEqualTo("subjectAccessRequest failed and max retry attempts (2) exhausted, id=null, event=GET_UNCLAIMED_REQUESTS")
      assertThat(actual.cause).isInstanceOf(InternalServerError::class.java)

      subjectAccessRequestApiMock.verifyGetUnclaimedSubjectAccessRequestsIsCalled(times = 3, token = AUTH_TOKEN)
    }

    @Test
    fun `get unclaimed requests does not retry on 401 status error`() {
      subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsWithStatus(401, AUTH_TOKEN)

      val actual = assertThrows<FatalSubjectAccessRequestException> { sarGateway.getUnclaimed(webClient) }

      assertThat(actual.message).isEqualTo("subjectAccessRequest failed with non-retryable error, id=null, event=GET_UNCLAIMED_REQUESTS, httpStatus=401 UNAUTHORIZED")

      subjectAccessRequestApiMock.verifyGetUnclaimedSubjectAccessRequestsIsCalled(times = 1, token = AUTH_TOKEN)
    }

    @Test
    fun `get unclaimed requests does not retry on 403 status error`() {
      subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsWithStatus(403, AUTH_TOKEN)

      val error = assertThrows<FatalSubjectAccessRequestException> { sarGateway.getUnclaimed(webClient) }

      assertThat(error.message).isEqualTo("subjectAccessRequest failed with non-retryable error, id=null, event=GET_UNCLAIMED_REQUESTS, httpStatus=403 FORBIDDEN")
      subjectAccessRequestApiMock.verifyGetUnclaimedSubjectAccessRequestsIsCalled(times = 1, token = AUTH_TOKEN)
    }

    @Test
    fun `get unclaimed requests throws exception when initial request errors with 5xx status and retry fails 4xx status`() {
      subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsWith500ThenFailsWith401ThenSucceeds(AUTH_TOKEN)

      val error = assertThrows<FatalSubjectAccessRequestException> { sarGateway.getUnclaimed(webClient) }

      assertThat(error.message).isEqualTo("subjectAccessRequest failed with non-retryable error, id=null, event=GET_UNCLAIMED_REQUESTS, httpStatus=401 UNAUTHORIZED")
      subjectAccessRequestApiMock.verifyGetUnclaimedSubjectAccessRequestsIsCalled(times = 2, token = AUTH_TOKEN)
    }

    @Test
    fun `get unclaimed throws the expected exception when the webclient gets a connection refused error`() {
      // Intentionally misconfigure the web client to trigger a connection refused error.
      val borkedWebClient = WebClient.builder()
        .baseUrl("http://localhost:${subjectAccessRequestApiMock.port() + 1}")
        .build()

      val error = assertThrows<SubjectAccessRequestRetryExhaustedException> { sarGateway.getUnclaimed(borkedWebClient) }

      assertThat(error.message).isEqualTo("subjectAccessRequest failed and max retry attempts (2) exhausted, id=null, event=GET_UNCLAIMED_REQUESTS")
      assertThat(error.cause).isInstanceOf(WebClientRequestException::class.java)
      assertThat(error.cause!!.message).contains("Connection refused")

      subjectAccessRequestApiMock.verifyZeroInteractions()
    }

    private fun assertGetUnclaimedSubjectAccessRequestsResponse(actual: Array<SubjectAccessRequest>?) {
      assertThat(actual).isNotNull
      assertThat(actual).hasSize(1)

      val sarRequest = actual?.get(0)!!

      assertThat(sarRequest.id.toString()).isEqualTo("72b5b7c3-a6e9-4b31-b0ba-5a00484714b6")
      assertThat(sarRequest.status).isEqualTo(Status.Pending)
      assertThat(sarRequest.dateFrom).isNull()
      assertThat(sarRequest.sarCaseReferenceNumber).isEqualTo("TEST_CASE_001")
      assertThat(sarRequest.services).isEqualTo("hmpps-complexity-of-need, https://localhost:8080/some-name-here")
      assertThat(sarRequest.nomisId).isEqualTo("666999")
      assertThat(sarRequest.ndeliusCaseReferenceId).isNull()
      assertThat(sarRequest.requestedBy).isEqualTo("BOB")
      assertThat(sarRequest.requestDateTime).isEqualTo(LocalDateTime.parse("2024-10-09T15:51:16.729913"))
      assertThat(sarRequest.claimDateTime).isNull()
      assertThat(sarRequest.claimAttempts).isEqualTo(0)
      assertThat(sarRequest.objectUrl).isNull()
      assertThat(sarRequest.lastDownloaded).isNull()
    }
  }

  @Nested
  inner class ClaimSubjectAccessRequestIntTest {

    @Test
    fun `claim Subject Access Request success`() {
      subjectAccessRequestApiMock.stubClaimSARReturnsStatus(200, subjectAccessRequestId, AUTH_TOKEN)

      sarGateway.claim(webClient, sarRequestMock)

      subjectAccessRequestApiMock.verifyClaimSubjectAccessRequestIsCalled(
        times = 1,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )
    }

    @Test
    fun `claim Subject Access Request throws FatalSubjectAccessRequestException when request fails with status 400`() {
      subjectAccessRequestApiMock.stubClaimSARReturnsStatus(400, subjectAccessRequestId, AUTH_TOKEN)

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        sarGateway.claim(webClient, sarRequestMock)
      }

      assertThat(actual.message).isEqualTo("subjectAccessRequest failed with non-retryable error: client 4xx response status, event=$CLAIM_REQUEST, id=$subjectAccessRequestId, httpStatus=400 BAD_REQUEST")
      assertThat(actual.cause).isNull()

      subjectAccessRequestApiMock.verifyClaimSubjectAccessRequestIsCalled(
        times = 1,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )
    }

    @Test
    fun `claim Subject Access Request throws SubjectAccessRequestException when request and retries fail with 5xx status error`() {
      subjectAccessRequestApiMock.stubClaimSARReturnsStatus(500, subjectAccessRequestId, AUTH_TOKEN)

      val actual = assertThrows<SubjectAccessRequestRetryExhaustedException> {
        sarGateway.claim(webClient, sarRequestMock)
      }

      assertThat(actual).isNotNull()
      assertThat(actual.message).isEqualTo("subjectAccessRequest failed and max retry attempts (2) exhausted, event=$CLAIM_REQUEST, id=$subjectAccessRequestId")
      assertThat(actual.cause).isInstanceOf(InternalServerError::class.java)

      subjectAccessRequestApiMock.verifyClaimSubjectAccessRequestIsCalled(
        times = 3,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )
    }

    @Test
    fun `claim Subject Access Request is successful when initial request fails with 5xx and the retry succeeds`() {
      subjectAccessRequestApiMock.stubClaimSARErrorsWith5xxOnInitialRequestAndReturnsStatusOnRetry(
        retryResponseStatus = 200,
        subjectAccessRequestId,
        AUTH_TOKEN,
      )

      sarGateway.claim(webClient, sarRequestMock)

      subjectAccessRequestApiMock.verifyClaimSubjectAccessRequestIsCalled(
        times = 2,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )
    }

    @Test
    fun `claim Subject Access Request throws FatalSubjectAccessRequestException does not retry on 4xx status error`() {
      subjectAccessRequestApiMock.stubClaimSARErrorsWith5xxOnInitialRequestAndReturnsStatusOnRetry(
        retryResponseStatus = 400,
        subjectAccessRequestId,
        AUTH_TOKEN,
      )

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        sarGateway.claim(webClient, sarRequestMock)
      }

      assertThat(actual.message).isEqualTo("subjectAccessRequest failed with non-retryable error: client 4xx response status, event=$CLAIM_REQUEST, id=$subjectAccessRequestId, httpStatus=400 BAD_REQUEST")
      assertThat(actual.cause).isNull()

      subjectAccessRequestApiMock.verifyClaimSubjectAccessRequestIsCalled(
        times = 2,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )
    }

    @Test
    fun `claim request throws the expected exception when the webclient gets a connection refused error`() {
      // Intentionally misconfigure the web client to trigger a connection refused error.
      val borkedWebClient = WebClient.builder()
        .baseUrl("http://localhost:${subjectAccessRequestApiMock.port() + 1}")
        .build()

      val error = assertThrows<SubjectAccessRequestRetryExhaustedException> {
        sarGateway.claim(borkedWebClient, sarRequestMock)
      }

      assertThat(error.message).isEqualTo("subjectAccessRequest failed and max retry attempts (2) exhausted, event=$CLAIM_REQUEST, id=$subjectAccessRequestId")
      assertThat(error.cause).isInstanceOf(WebClientRequestException::class.java)
      assertThat(error.cause!!.message).contains("Connection refused")

      subjectAccessRequestApiMock.verifyZeroInteractions()
    }
  }

  @Nested
  inner class CompleteSubjectAccessRequestIntTest {

    @Test
    fun `complete subject access request success`() {
      subjectAccessRequestApiMock.stubCompleteSubjectAccessRequest(
        responseStatus = 200,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )

      sarGateway.complete(webClient, sarRequestMock)

      subjectAccessRequestApiMock.verifyCompleteSubjectAccessRequestIsCalled(
        times = 1,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )
    }

    @Test
    fun `complete subject access request retries on 5xx status`() {
      subjectAccessRequestApiMock.stubCompleteSubjectAccessRequest(
        responseStatus = 503,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )

      val actual = assertThrows<SubjectAccessRequestRetryExhaustedException> {
        sarGateway.complete(webClient, sarRequestMock)
      }

      assertThat(actual.message).isEqualTo("subjectAccessRequest failed and max retry attempts (2) exhausted, id=$subjectAccessRequestId, event=$COMPLETE_REQUEST")
      assertThat(actual.cause).isInstanceOf(ServiceUnavailable::class.java)

      subjectAccessRequestApiMock.verifyCompleteSubjectAccessRequestIsCalled(
        times = 3,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )
    }

    @Test
    fun `complete subject access request throws FatalSubjectAccessRequestException and does not retry on 4xx status`() {
      subjectAccessRequestApiMock.stubCompleteSubjectAccessRequest(
        responseStatus = 401,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        sarGateway.complete(webClient, sarRequestMock)
      }

      assertThat(actual.message).isEqualTo("subjectAccessRequest failed with non-retryable error, event=$COMPLETE_REQUEST, id=$subjectAccessRequestId, httpStatus=401 UNAUTHORIZED")
      assertThat(actual.cause).isNull()

      subjectAccessRequestApiMock.verifyCompleteSubjectAccessRequestIsCalled(
        times = 1,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )
    }

    @Test
    fun `complete subject access request is successful when first request fails with 5xx and retry succeeds`() {
      subjectAccessRequestApiMock.stubCompleteSubjectAccessRequestFailsWith5xxOnFirstRequestSucceedsOnRetry(
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )

      sarGateway.complete(webClient, sarRequestMock)

      subjectAccessRequestApiMock.verifyCompleteSubjectAccessRequestIsCalled(
        times = 2,
        sarId = subjectAccessRequestId,
        token = AUTH_TOKEN,
      )
    }

    @Test
    fun `complete request throws the expected exception when the webclient gets a connection refused error`() {
      // Intentionally misconfigure the web client to trigger a connection refused error.
      val borkedWebClient = WebClient.builder()
        .baseUrl("http://localhost:${subjectAccessRequestApiMock.port() + 1}")
        .build()

      val error = assertThrows<SubjectAccessRequestRetryExhaustedException> {
        sarGateway.complete(borkedWebClient, sarRequestMock)
      }

      assertThat(error.message).isEqualTo("subjectAccessRequest failed and max retry attempts (2) exhausted, id=$subjectAccessRequestId, event=${COMPLETE_REQUEST}")
      assertThat(error.cause).isInstanceOf(WebClientRequestException::class.java)
      assertThat(error.cause!!.message).contains("Connection refused")

      subjectAccessRequestApiMock.verifyZeroInteractions()
    }
  }
}
