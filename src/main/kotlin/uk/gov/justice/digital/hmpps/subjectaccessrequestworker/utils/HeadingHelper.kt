package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import java.util.Locale

class HeadingHelper {
  companion object {
    fun format(heading: String): String = heading.replace("-", " ")
      .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
  }
}
