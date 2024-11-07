package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi

class PrisonApiClientIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var prisonApiClient: PrisonApiClient

  @Test
  fun `should get prisoner`() {
    hmppsAuth.stubGrantToken()
    prisonApi.stubGetOffenderDetails()

    val response = prisonApiClient.getOffenderName("A9999AA")

    assertThat(response).isNotNull
    assertThat(response).isEqualTo("REACHER, Joe")
  }
}
