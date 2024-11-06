package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase

class ProcessDataHelperTest : IntegrationTestBase() {

  @Test
  fun `camelToSentence() returns key formatted`() {
    val key = "keyToFormat"

    val formattedKey = ProcessDataHelper.camelToSentence(key)

    assertThat(formattedKey).isEqualTo("Key to format")
  }
}
