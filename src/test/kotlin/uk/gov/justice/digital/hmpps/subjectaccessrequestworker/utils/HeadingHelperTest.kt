package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase

class HeadingHelperTest : IntegrationTestBase() {

  @Test
  fun `format() returns heading formatted`() {
    val heading = "heading-that-is-hard-to-read"

    val formattedHeading = HeadingHelper.format(heading)

    assertThat(formattedHeading).isEqualTo("Heading that is hard to read")
  }
}
