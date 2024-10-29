package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import org.assertj.core.api.Assertions.assertThat

fun assertExpectedErrorMessage(actual: Throwable, prefix: String, vararg params: Pair<String, *>) {
  val formattedParams = params.joinToString(", ") { entry ->
    "${entry.first}=${entry.second}"
  }
  val expected = "$prefix $formattedParams"
  assertThat(actual.message).isEqualTo(expected)
}
