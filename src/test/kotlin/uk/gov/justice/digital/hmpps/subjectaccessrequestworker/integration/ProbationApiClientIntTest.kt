package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ProbationApiExtension.Companion.probationApi

@ActiveProfiles(profiles = ["test"])
class ProbationApiClientIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var probationApiClient: ProbationApiClient

  @Test
  fun `should get offender from probation api`() {
    hmppsAuth.stubGrantToken()
    probationApi.stubGetOffenderDetails()

    val response = probationApiClient.getOffenderName("A999999")

    assertThat(response).isNotNull
    assertThat(response).isEqualTo("WIMP, Eric")
  }
}
