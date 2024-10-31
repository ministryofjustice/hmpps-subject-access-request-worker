package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.ActiveProfiles
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

    val thrown = assertThrows<RuntimeException> { hmppsAuthGateway.getClientToken() }

    assertThat(thrown.message).isEqualTo("localhost:9090 is unavailable.")
  }

  @Test
  fun `get throws an exception if credentials are invalid`() {
    hmppsAuth.stubUnauthorizedForGetOAAuthToken()

    val thrown = assertThrows<RuntimeException> { hmppsAuthGateway.getClientToken() }

    assertThat(thrown.message).isEqualTo("Invalid credentials used.")
  }
}
