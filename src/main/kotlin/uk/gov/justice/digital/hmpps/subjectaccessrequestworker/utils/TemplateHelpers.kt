package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository

@Service
class TemplateHelpers(
  private val prisonDetailsRepository: PrisonDetailsRepository,
) {
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

  fun getPrisonName(caseloadId: String): String {
    if (caseloadId.isEmpty() || caseloadId == "") return "No Data Held"
    val prisonDetails = prisonDetailsRepository.findByPrisonId(caseloadId)

    return prisonDetails?.prisonName ?: caseloadId
  }
}
