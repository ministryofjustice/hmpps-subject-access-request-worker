package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SubjectAccessRequestDataFormatterTest {

  companion object {

    @JvmStatic
    fun testCases(): List<TestCase> = listOf(
      TestCase(null, null, ""),
      TestCase("", null, ""),
      TestCase(null, "", ""),
      TestCase("", "", ""),
      TestCase("revolver", "ocelot", "OCELOT, Revolver"),
      TestCase("soLiD", "SnAkE", "SNAKE, Solid"),
      TestCase("     ", "     ", ""),
    )
  }

  data class TestCase(val forename: String?, val surname: String?, val expectedResult: String)

  @ParameterizedTest
  @MethodSource("testCases")
  fun `format name should return the expected value`(testCase: TestCase) {
    val result = formatName(testCase.forename, testCase.surname)
    assertThat(result).isEqualTo(testCase.expectedResult)
  }
}
