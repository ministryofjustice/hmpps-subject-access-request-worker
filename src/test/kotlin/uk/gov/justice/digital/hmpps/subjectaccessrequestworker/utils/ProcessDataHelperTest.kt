package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase

class ProcessDataHelperTest : IntegrationTestBase() {

  @Test
  fun `camelToSentence() returns key formatted`() = runTest {
    val key = "keyToFormat"

    val formattedKey = ProcessDataHelper.camelToSentence(key)

    Assertions.assertThat(formattedKey).isEqualTo("Key to format")
  }
}
