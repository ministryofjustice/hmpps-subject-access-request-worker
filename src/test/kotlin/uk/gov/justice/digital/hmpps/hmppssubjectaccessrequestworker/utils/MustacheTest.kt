package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.integration.IntegrationTestBase

class MustacheTest : IntegrationTestBase() {

  @Test
  fun `Mustache compiles and returns 0`() {
    val response = Mustache().compile("data")
    Assertions.assertThat(response).isEqualTo(0)
  }
}