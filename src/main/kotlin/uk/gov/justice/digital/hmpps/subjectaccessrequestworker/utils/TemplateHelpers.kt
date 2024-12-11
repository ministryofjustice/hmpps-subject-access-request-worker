package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.apache.commons.lang3.StringUtils.isBlank
import org.apache.commons.lang3.StringUtils.leftPad
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import java.lang.String.format

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

  fun getPrisonName(caseloadId: String?): String {
    if (caseloadId == null || caseloadId.isEmpty() || caseloadId == "") return "No Data Held"
    val prisonDetails = prisonDetailsRepository.findByPrisonId(caseloadId)

    return prisonDetails?.prisonName ?: caseloadId
  }

  fun getUserLastName(userId: String?): String {
    if (isBlank(userId)) return "No Data Held"
    val userDetails = userDetailsRepository.findByUsername(userId!!)

    return userDetails?.lastName ?: userId
  }

  fun convertBoolean(input: Any?): Any = when {
    input is Boolean && input -> "Yes"
    input is Boolean && !input -> "No"
    input == null -> "No Data Held"
    else -> input
  }

  fun buildDate(year: String?, month: String?, day: String?): String {
    if (isBlank(year) || isBlank(month) || isBlank(day)) {
      return "No Data Held"
    }
    return formatDate(format("%s-%s-%s", leftPad(year, 4, "0"), leftPad(month, 2, "0"), leftPad(day, 2, "0")))
  }

  fun buildDateNumber(year: Number?, month: Number?, day: Number?): String =
    buildDate(year?.toInt()?.toString(), month?.toInt()?.toString(), day?.toInt()?.toString())
}
