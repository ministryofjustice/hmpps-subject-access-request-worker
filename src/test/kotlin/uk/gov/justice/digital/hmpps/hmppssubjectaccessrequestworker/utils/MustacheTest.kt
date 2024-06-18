package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.integration.IntegrationTestBase

class MustacheTest : IntegrationTestBase() {

  @Test
  fun `Mustache compiles and returns 0`() {
    val response = Mustache().compile("template.mustache")
    Assertions.assertThat(response).isEqualTo(0)
    assertThrows<Exception> { Mustache().compile("notemplate.mustache") }
  }
}