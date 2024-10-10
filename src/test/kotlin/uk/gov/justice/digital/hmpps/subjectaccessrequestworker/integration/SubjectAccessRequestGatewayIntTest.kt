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
  fun `get unclaimed requests fails on first attempt but is successful on retry`() {
    subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsOnFirstAttempt(AUTH_TOKEN)

    val result: Array<SubjectAccessRequest>? = sarGateway.getUnclaimed(webClient)

    assertSarResponse(result)
    subjectAccessRequestApiMock.verify(2, getRequestedFor(urlPathEqualTo("/api/subjectAccessRequests")))
  }

  @Test
  fun `get unclaimed requests throws exception when the initial request and retries fail`() {
    subjectAccessRequestApiMock.stubGetUnclaimedRequestsFailsAllAttempts(AUTH_TOKEN)

    val ex = assertThrows<Exception> { sarGateway.getUnclaimed(webClient)  }

    println(ex.javaClass.simpleName)

    subjectAccessRequestApiMock.verify(3, getRequestedFor(urlPathEqualTo("/api/subjectAccessRequests")))
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