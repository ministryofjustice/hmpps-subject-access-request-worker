package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import java.util.Locale

class HeadingHelper {
  companion object {
    fun format(heading: String): String {
      return heading.replace("-", " ")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
  }
}

class ProcessDataHelper {
  companion object {
    fun camelToSentence(input: String): String {
      return input //TODO: Tests and complete this function
    }
  }
}