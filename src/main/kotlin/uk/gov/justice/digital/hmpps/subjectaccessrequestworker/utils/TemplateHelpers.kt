package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.apache.commons.lang3.StringUtils.isBlank
import org.apache.commons.lang3.StringUtils.isNotBlank
import org.apache.commons.lang3.StringUtils.leftPad
import org.apache.commons.lang3.StringUtils.splitByCharacterTypeCamelCase
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import java.lang.String.format
import java.time.LocalDateTime

@Service
class TemplateHelpers(
  private val prisonDetailsRepository: PrisonDetailsRepository,
  private val userDetailsRepository: UserDetailsRepository,
  private val locationDetailsRepository: LocationDetailsRepository,
  private val locationsApiClient: LocationsApiClient,
  private val nomisMappingApiClient: NomisMappingApiClient,
) {
  companion object {
    const val NO_DATA_HELD = "No Data Held"
  }

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
    if (input == null || input == "") return NO_DATA_HELD
    return input
  }

  fun optionalString(input: Any?): Any {
    if (input == null) return NO_DATA_HELD

    if (input is String) {
      return if (isNotBlank(input)) input else NO_DATA_HELD
    }
    throw SubjectAccessRequestTemplatingException(
      message = "required type String or null, but actual type was ${input::class.simpleName}",
    )
  }

  fun getIndexPlusOne(elementIndex: Int?): Int? {
    if (elementIndex != null) {
      return elementIndex + 1
    }
    return null
  }

  fun getPrisonName(caseloadId: String?): String {
    if (caseloadId == null || caseloadId.isEmpty() || caseloadId == "") return NO_DATA_HELD
    val prisonDetails = prisonDetailsRepository.findByPrisonId(caseloadId)

    return prisonDetails?.prisonName ?: caseloadId
  }

  fun getUserLastName(userId: String?): String {
    if (isBlank(userId)) return NO_DATA_HELD
    val userDetails = userDetailsRepository.findByUsername(userId!!)

    return userDetails?.lastName ?: userId
  }

  fun getLocationNameByDpsId(dpsId: String?): String = if (isBlank(dpsId)) {
    NO_DATA_HELD
  } else {
    getLocationByDpsId(dpsId!!) ?: dpsId
  }

  fun getLocationNameByNomisId(nomisId: Int?): String = if (nomisId == null) {
    NO_DATA_HELD
  } else {
    locationDetailsRepository.findByNomisId(nomisId)?.name ?: nomisMappingApiClient.getNomisLocationMapping(nomisId)
      ?.let { getLocationByDpsId(it.dpsLocationId) } ?: nomisId.toString()
  }

  private fun getLocationByDpsId(dpsId: String): String? = locationDetailsRepository.findByDpsId(dpsId)?.name ?: locationsApiClient.getLocationDetails(dpsId)
    ?.let { it.localName ?: it.pathHierarchy }

  fun convertBoolean(input: Any?): Any = when {
    input is Boolean && input -> "Yes"
    input is Boolean && !input -> "No"
    input == 1 || input == "1" || input == "true" -> "Yes"
    input == 0 || input == "0" || input == "false" -> "No"
    input == null -> NO_DATA_HELD
    else -> input
  }

  fun buildDate(year: String?, month: String?, day: String?): String {
    if (isBlank(year) || isBlank(month) || isBlank(day)) {
      return NO_DATA_HELD
    }
    return formatDate(format("%s-%s-%s", leftPad(year, 4, "0"), leftPad(month, 2, "0"), leftPad(day, 2, "0")))
  }

  fun buildDateNumber(year: Number?, month: Number?, day: Number?): String = buildDate(year?.toInt()?.toString(), month?.toInt()?.toString(), day?.toInt()?.toString())

  fun eq(input: String?, value: String?): Boolean = input == value

  fun convertCamelCase(input: String?): String {
    if (input == null || input == "") return NO_DATA_HELD
    if (input.contains(" ")) return input
    return splitByCharacterTypeCamelCase(input).joinToString(" ").lowercase()
  }
}
