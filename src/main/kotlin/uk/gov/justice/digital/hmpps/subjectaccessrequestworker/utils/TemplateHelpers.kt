package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository

@Service
class TemplateHelpers(
  private val prisonDetailsRepository: PrisonDetailsRepository,
  private val userDetailsRepository: UserDetailsRepository,
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

  fun getUserLastName(userId: String): String {
    if (userId.isEmpty() || userId == "") return "No Data Held"
    val userDetails = userDetailsRepository.findByUsername(userId)

    return userDetails?.lastName ?: userId
  }
}
