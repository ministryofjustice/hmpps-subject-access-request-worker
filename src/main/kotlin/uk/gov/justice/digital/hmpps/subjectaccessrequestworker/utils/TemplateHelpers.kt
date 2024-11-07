package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.springframework.stereotype.Service

@Service
class TemplateHelpers() {
  fun formatDate(input: String?): String {
    if (input == null) return ""
    return DateConversionHelper().convertDates(input)
  }

  fun optionalValue(input: Any?): Any {
    if (input == null || input == "") return "No Data Held"
    return input
  }

  fun getIndexPlusOne(elementIndex: Int?): Int? {
    if (elementIndex != null) {
      return elementIndex + 1
    }
    return null
  }
}
