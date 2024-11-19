package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.apache.commons.lang3.StringUtils

fun formatName(forename: String?, surname: String?): String {
  if (StringUtils.isEmpty(surname?.trim()) || StringUtils.isEmpty(forename?.trim())) {
    return ""
  }

  return "${surname!!.uppercase()}, ${forename!!.lowercase().replaceFirstChar { it.titlecase() }}"
}
