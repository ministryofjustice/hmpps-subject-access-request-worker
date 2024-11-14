package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.HmppsAuthGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth

@ExtendWith(HmppsAuthApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
class HmppsAuthIntTest {

  @Autowired
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

    val ex = assertThrows<SubjectAccessRequestException> { hmppsAuthGateway.getClientToken() }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "authGateway get auth token WebclientResponseException,",
      "event" to ProcessingEvent.ACQUIRE_AUTH_TOKEN,
      "id" to null,
      "authority" to "localhost:${hmppsAuth.port()}",
      "httpStatus" to 503,
      "body" to "",
    )
  }

  @Test
  fun `get throws an exception if credentials are invalid`() {
    hmppsAuth.stubUnauthorizedForGetOAAuthToken()

    val ex = assertThrows<RuntimeException> { hmppsAuthGateway.getClientToken() }

    assertExpectedErrorMessage(
      actual = ex,
      prefix = "authGateway get auth token WebclientResponseException,",
      "event" to ProcessingEvent.ACQUIRE_AUTH_TOKEN,
      "id" to null,
      "authority" to "localhost:${hmppsAuth.port()}",
      "httpStatus" to 401,
      "body" to "",
    )
  }
}
