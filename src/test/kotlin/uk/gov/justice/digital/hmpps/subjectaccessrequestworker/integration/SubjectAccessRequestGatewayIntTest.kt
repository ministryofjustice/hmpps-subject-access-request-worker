package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
import org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.HmppsAuthGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.SubjectAccessRequestApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.SubjectAccessRequestApiExtension.Companion.subjectAccessRequestApiMock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDateTime

@ExtendWith(SubjectAccessRequestApiExtension::class)
class SubjectAccessRequestGatewayIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var sarGateway: SubjectAccessRequestGateway

  @MockBean
  lateinit var hmppsAuthGateway: HmppsAuthGateway

  lateinit var webClient: WebClient

  companion object {
    const val AUTH_TOKEN = "ABC1234"
  }

  @BeforeEach
  fun setup() {
    webClient = WebClient.builder()
      .baseUrl("http://localhost:${subjectAccessRequestApiMock.port()}")
      .build()

    whenever(hmppsAuthGateway.getClientToken())
      .thenReturn(AUTH_TOKEN)
  }

  @Test
  fun `get unclaimed requests is successful`() {
    subjectAccessRequestApiMock.stubGetUnclaimedRequestsSuccess(AUTH_TOKEN)

    val result: Array<SubjectAccessRequest>? = sarGateway.getUnclaimed(webClient)

    assertSarResponse(result)
    subjectAccessRequestApiMock.verify(1, getRequestedFor(urlPathEqualTo("/api/subjectAccessRequests")))
  }

  @Test
  fun `get unclaimed requests fails with 5xx status on first attempt but is successful on retry`() {
    subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsOnFirstAttemptWithStatus(500, AUTH_TOKEN)

    val result: Array<SubjectAccessRequest>? = sarGateway.getUnclaimed(webClient)

    assertSarResponse(result)
    subjectAccessRequestApiMock.verify(2, getRequestedFor(urlPathEqualTo("/api/subjectAccessRequests")))
  }

  @Test
  fun `get unclaimed requests throws exception when all requests and retries fail with 5xx status`() {
    subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsWithStatus(500, AUTH_TOKEN)

    val error = assertThrows<Exception> { sarGateway.getUnclaimed(webClient) }

    assertThat(error).isInstanceOf(InternalServerError::class.java)
    subjectAccessRequestApiMock.verify(3, getRequestedFor(urlPathEqualTo("/api/subjectAccessRequests")))
  }

  @Test
  fun `get unclaimed requests does not retry on 401 status error`() {
    subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsWithStatus(401, AUTH_TOKEN)

    val error = assertThrows<Exception> { sarGateway.getUnclaimed(webClient) }

    assertThat(error).isInstanceOf(Unauthorized::class.java)
    subjectAccessRequestApiMock.verify(1, getRequestedFor(urlPathEqualTo("/api/subjectAccessRequests")))
  }

  @Test
  fun `get unclaimed requests does not retry on 403 status error`() {
    subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsWithStatus(403, AUTH_TOKEN)

    val error = assertThrows<Exception> { sarGateway.getUnclaimed(webClient) }

    assertThat(error).isInstanceOf(Forbidden::class.java)
    subjectAccessRequestApiMock.verify(1, getRequestedFor(urlPathEqualTo("/api/subjectAccessRequests")))
  }

  @Test
  fun `get unclaimed requests throws exception when initial request errors with 5xx status and retry fails 4xx status`() {
    subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsWith500ThenFailsWith401ThenSucceeds(AUTH_TOKEN)

    val error = assertThrows<Exception> { sarGateway.getUnclaimed(webClient) }

    assertThat(error).isInstanceOf(Unauthorized::class.java)
    subjectAccessRequestApiMock.verify(2, getRequestedFor(urlPathEqualTo("/api/subjectAccessRequests")))
  }

  fun assertSarResponse(actual: Array<SubjectAccessRequest>?) {
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