package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.apache.commons.lang3.StringUtils.isBlank
import org.apache.commons.lang3.StringUtils.leftPad
import org.apache.commons.lang3.StringUtils.splitByCharacterTypeCamelCase
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import java.lang.String.format
import java.time.LocalDateTime

@Service
class TemplateHelpers(
  private val prisonDetailsRepository: PrisonDetailsRepository,
  private val userDetailsRepository: UserDetailsRepository,
) {
  fun formatDate(input: Any?): String {
    if (input == null) return ""
    return when (input) {
      is String -> DateConversionHelper().convertDates(input)
      is List<*> -> DateConversionHelper().convertDates(
        LocalDateTime.of(
          input.getOrDefault(0, 1),
          input.getOrDefault(1, 1),
          input.getOrDefault(2, 1),
          input.getOrDefault(3),
          input.getOrDefault(4),
          input.getOrDefault(5),
        ).toString(),
      )

      else -> input.toString()
    }
  }

  fun List<*>.getOrDefault(index: Int, default: Int = 0): Int {
    if (this.size > index) {
      val listValue = this[index]
      return when (listValue) {
          is Number -> listValue.toInt()
          is String -> listValue.toInt()
          else -> default
      }
    }
    return default
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
    input == 1 || input == "1" || input == "true" -> "Yes"
    input == 0 || input == "0" || input == "false" -> "No"
    input == null -> "No Data Held"
    else -> input
  }

  fun buildDate(year: String?, month: String?, day: String?): String {
    if (isBlank(year) || isBlank(month) || isBlank(day)) {
      return "No Data Held"
    }
    return formatDate(format("%s-%s-%s", leftPad(year, 4, "0"), leftPad(month, 2, "0"), leftPad(day, 2, "0")))
  }

  fun buildDateNumber(year: Number?, month: Number?, day: Number?): String = buildDate(year?.toInt()?.toString(), month?.toInt()?.toString(), day?.toInt()?.toString())

  fun eq(input: String?, value: String?): Boolean = input == value

  fun convertCamelCase(input: String?): String {
    if (input == null || input == "") return "No Data Held"
    if (input.contains(" ")) return input
    return splitByCharacterTypeCamelCase(input).joinToString(" ").lowercase()
  }
}
