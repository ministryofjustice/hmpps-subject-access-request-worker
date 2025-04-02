package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.HmppsAuthGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth

@ExtendWith(HmppsAuthApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
class HmppsAuthIntTest {

  @Autowiredq
  private lateinit var hmppsAuthGateway: HmppsAuthGateway

  @Test
  fun `get token`() {
    hmppsAuth.stubGetOAuthToken("hmpps-subject-access-request", "clientsecret")

    val token = hmppsAuthGateway.getClientToken()

    assertThat(token).isNotBlank
    assertThat(token).isEqualTo("ABCDE")
  }

  @Test
  fun `get token service unavailable`() {
    hmppsAuth.stubServiceUnavailableForGetOAuthToken()

    val exception = assertThrows<SubjectAccessRequestException> { hmppsAuthGateway.getClientToken() }

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "authGateway get auth token WebclientResponseException,",
      expectedCause = WebClientResponseException.ServiceUnavailable::class.java,
      expectedEvent = ProcessingEvent.ACQUIRE_AUTH_TOKEN,
      expectedSubjectAccessRequest = null,
      expectedParams = mapOf(
        "authority" to "localhost:${hmppsAuth.port()}",
        "httpStatus" to 503,
        "body" to "",
      ),
    )
  }

  @Test
  fun `get throws an exception if credentials are invalid`() {
    hmppsAuth.stubUnauthorizedForGetOAAuthToken()

    val exception = assertThrows<SubjectAccessRequestException> { hmppsAuthGateway.getClientToken() }

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "authGateway get auth token WebclientResponseException",
      expectedCause = WebClientResponseException.Unauthorized::class.java,
      expectedEvent = ProcessingEvent.ACQUIRE_AUTH_TOKEN,
      expectedSubjectAccessRequest = null,
      expectedParams = mapOf(
        "authority" to "localhost:${hmppsAuth.port()}",
        "httpStatus" to 401,
        "body" to "",
      ),
    )
  }
}
