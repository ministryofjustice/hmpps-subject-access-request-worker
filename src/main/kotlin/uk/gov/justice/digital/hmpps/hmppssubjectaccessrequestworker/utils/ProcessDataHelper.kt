package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import java.util.*

class ProcessDataHelper {
  companion object {
    fun camelToSentence(input: String): String {
      val capitalLetters = "[A-Z]".toRegex()
      return input.replace(capitalLetters, " $0").lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
  }
}
