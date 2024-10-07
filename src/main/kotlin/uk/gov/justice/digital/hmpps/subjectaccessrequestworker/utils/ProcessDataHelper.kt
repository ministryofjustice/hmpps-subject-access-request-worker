package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

class ProcessDataHelper {
  companion object {
    fun camelToSentence(input: String): String {
      val capitalLetters = "[A-Z]".toRegex()
      return input.replace(capitalLetters, " $0").lowercase()
        .replaceFirstChar { it.uppercaseChar() }
    }
  }
}
