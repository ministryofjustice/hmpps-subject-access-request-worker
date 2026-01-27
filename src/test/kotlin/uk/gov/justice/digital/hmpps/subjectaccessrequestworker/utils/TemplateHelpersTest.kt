package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.LocationsApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.LocationsApiClient.LocationDetailsResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.NomisMappingApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.NomisMappingApiClient.NomisLocationMapping
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.LocationDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import java.util.stream.Stream

private const val LOCATION_DPS_ID = "28953d06-d379-450c-9ec4-b5993ce5cd4f"
private const val LOCATION_NOMIS_ID = 4324567

class TemplateHelpersTest {

  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val locationDetailsRepository: LocationDetailsRepository = mock()
  private val locationsApiClient: LocationsApiClient = mock()
  private val nomisMappingApiClient: NomisMappingApiClient = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository, locationDetailsRepository, locationsApiClient, nomisMappingApiClient)

  @Nested
  inner class GetElementNumberTest {
    @Test
    fun `getElementNumber returns element number plus 1`() {
      val response = templateHelpers.getIndexPlusOne(1)
      assertThat(response).isEqualTo(2)
    }

    @Test
    fun `getElementNumber returns null given null`() {
      val response = templateHelpers.getIndexPlusOne(null)
      assertThat(response).isNull()
    }
  }

  @Nested
  inner class OptionalValueTest {
    @Test
    fun `optionalValue returns No Data Held if null`() {
      val response = templateHelpers.optionalValue(null)
      assertThat(response).isEqualTo("No Data Held")
    }

    @Test
    fun `optionalValue returns No Data Held if empty string`() {
      val response = templateHelpers.optionalValue("")
      assertThat(response).isEqualTo("No Data Held")
    }

    @Test
    fun `optionalValue returns input when not empty`() {
      val response = templateHelpers.optionalValue("BOB")
      assertThat(response).isEqualTo("BOB")
    }
  }

  @Nested
  inner class FormatDateTest {

    @Test
    fun `formatDate returns empty string if input is null`() {
      val response = templateHelpers.formatDate(null)
      assertThat(response).isEqualTo("")
    }

    @Test
    fun `formatDate returns formatted date for valid string input`() {
      val response = templateHelpers.formatDate("2023-10-01")
      assertThat(response).isEqualTo("01 October 2023")
    }

    @ParameterizedTest
    @MethodSource("uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpersTest#dateArrayValues")
    fun `formatDate returns formatted date for valid array input`(input: List<*>, expectedValue: String) {
      val response = templateHelpers.formatDate(input)
      assertThat(response).isEqualTo(expectedValue)
    }
  }

  @Nested
  inner class GetPrisonNameTest {
    @Test
    fun `getPrisonName returns prison name`() {
      whenever(prisonDetailsRepository.findByPrisonId("MDI")).thenReturn(PrisonDetail("MDI", "Moorland (HMP & YOI)"))
      val response = templateHelpers.getPrisonName("MDI")
      assertThat(response).isEqualTo("Moorland (HMP & YOI)")
    }

    @Test
    fun `getPrisonName returns No Data Held if null`() {
      val response = templateHelpers.getPrisonName("")
      assertThat(response).isEqualTo("No Data Held")
    }
  }

  @Nested
  inner class GetUserLastNameTest {
    @Test
    fun `getUserLastName returns user last name`() {
      whenever(userDetailsRepository.findByUsername("AQ987Z")).thenReturn(UserDetail("AQ987Z", "Johnson"))
      val response = templateHelpers.getUserLastName("AQ987Z")
      assertThat(response).isEqualTo("Johnson")
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", " "])
    fun `getUserLastName returns No Data Held if null`(input: String?) {
      val response = templateHelpers.getUserLastName(input)
      assertThat(response).isEqualTo("No Data Held")
    }
  }

  @Nested
  inner class GetLocationNameByDpsIdTest {
    @Test
    fun `getLocationNameByDpsId returns location name from database`() {
      whenever(locationDetailsRepository.findByDpsId(LOCATION_DPS_ID)).thenReturn(LocationDetail(LOCATION_DPS_ID, LOCATION_NOMIS_ID, "PROPERTY BOX 27"))
      val response = templateHelpers.getLocationNameByDpsId(LOCATION_DPS_ID)
      assertThat(response).isEqualTo("PROPERTY BOX 27")
    }

    @Test
    fun `getLocationNameByDpsId returns location name from api`() {
      whenever(locationDetailsRepository.findByDpsId(LOCATION_DPS_ID)).thenReturn(null)
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(LocationDetailsResponse(LOCATION_DPS_ID, "PROPERTY BOX 27", "PROP_BOXES-PB027"))
      val response = templateHelpers.getLocationNameByDpsId(LOCATION_DPS_ID)
      assertThat(response).isEqualTo("PROPERTY BOX 27")
    }

    @Test
    fun `getLocationNameByDpsId returns location name from api when no localname value`() {
      whenever(locationDetailsRepository.findByDpsId(LOCATION_DPS_ID)).thenReturn(null)
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(LocationDetailsResponse(LOCATION_DPS_ID, null, "PROP_BOXES-PB027"))
      val response = templateHelpers.getLocationNameByDpsId(LOCATION_DPS_ID)
      assertThat(response).isEqualTo("PROP_BOXES-PB027")
    }

    @Test
    fun `getLocationNameByDpsId returns original id when not found from api`() {
      whenever(locationDetailsRepository.findByDpsId(LOCATION_DPS_ID)).thenReturn(null)
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(null)
      val response = templateHelpers.getLocationNameByDpsId(LOCATION_DPS_ID)
      assertThat(response).isEqualTo(LOCATION_DPS_ID)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", " "])
    fun `getUserLastNameByDpsId returns No Data Held if null`(input: String?) {
      val response = templateHelpers.getLocationNameByDpsId(input)
      assertThat(response).isEqualTo("No Data Held")
    }
  }

  @Nested
  inner class GetLocationNameByNomisIdTest {
    @Test
    fun `getLocationNameByNomisId returns location name from database`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(LocationDetail(LOCATION_DPS_ID, LOCATION_NOMIS_ID, "PROPERTY BOX 27"))
      val response = templateHelpers.getLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isEqualTo("PROPERTY BOX 27")
    }

    @Test
    fun `getLocationNameByNomisId returns location name from api`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(null)
      whenever(nomisMappingApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)).thenReturn(NomisLocationMapping(LOCATION_DPS_ID, LOCATION_NOMIS_ID))
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(LocationDetailsResponse(LOCATION_DPS_ID, "PROPERTY BOX 27", "PROP_BOXES-PB027"))
      val response = templateHelpers.getLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isEqualTo("PROPERTY BOX 27")
    }

    @Test
    fun `getLocationNameByNomisId returns location name from api when no localname value`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(null)
      whenever(nomisMappingApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)).thenReturn(NomisLocationMapping(LOCATION_DPS_ID, LOCATION_NOMIS_ID))
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(LocationDetailsResponse(LOCATION_DPS_ID, null, "PROP_BOXES-PB027"))
      val response = templateHelpers.getLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isEqualTo("PROP_BOXES-PB027")
    }

    @Test
    fun `getLocationNameByNomisId returns original id when not found from locations api`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(null)
      whenever(nomisMappingApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)).thenReturn(NomisLocationMapping(LOCATION_DPS_ID, LOCATION_NOMIS_ID))
      whenever(locationsApiClient.getLocationDetails(LOCATION_DPS_ID)).thenReturn(null)
      val response = templateHelpers.getLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isEqualTo(LOCATION_NOMIS_ID.toString())
    }

    @Test
    fun `getLocationNameByNomisId returns original id when nomis mapping not found`() {
      whenever(locationDetailsRepository.findByNomisId(LOCATION_NOMIS_ID)).thenReturn(null)
      whenever(nomisMappingApiClient.getNomisLocationMapping(LOCATION_NOMIS_ID)).thenReturn(null)
      val response = templateHelpers.getLocationNameByNomisId(LOCATION_NOMIS_ID)
      assertThat(response).isEqualTo(LOCATION_NOMIS_ID.toString())
    }

    @ParameterizedTest
    @NullSource
    fun `getLocationNameByNomisId returns No Data Held if null`(input: Int?) {
      val response = templateHelpers.getLocationNameByNomisId(input)
      assertThat(response).isEqualTo("No Data Held")
    }
  }

  @Nested
  inner class ConvertBooleanTest {
    @ParameterizedTest
    @CsvSource(
      value = [
        "true  | Yes",
        "false | No",
        "      | No Data Held",
      ],
      delimiterString = "|",
    )
    fun `convertBoolean returns yes or no value when boolean`(inputValue: Boolean?, expectedValue: String) {
      val response = templateHelpers.convertBoolean(inputValue)
      assertThat(response).isEqualTo(expectedValue)
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "1    | Yes",
        "0    | No",
        "     | No Data Held",
      ],
      delimiterString = "|",
    )
    fun `convertBoolean returns yes or no value when 1 or 0`(inputValue: Int?, expectedValue: String) {
      val response = templateHelpers.convertBoolean(inputValue)
      assertThat(response).isEqualTo(expectedValue)
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "true           | Yes",
        "false          | No",
        "1              | Yes",
        "0              | No",
        "Yes            | Yes",
        "No             | No",
        "               | No Data Held",
        "something else | something else",
      ],
      delimiterString = "|",
    )
    fun `convertBoolean returns original value when not boolean or 1 or 0`(inputValue: String?, expectedValue: String) {
      val response = templateHelpers.convertBoolean(inputValue)
      assertThat(response).isEqualTo(expectedValue)
    }
  }

  @Nested
  inner class BuildDateTest {
    @ParameterizedTest
    @CsvSource(
      value = [
        "2024 | 11    | null",
        "2024 | 11    | ",
        "2024 | null  | 15",
        "2024 |       | 15",
        "null | 11    | 15",
        "     | 11    | 15",
        "null | null  | null",
        "     |       |",
      ],
      delimiterString = "|",
      nullValues = ["null"],
    )
    fun `buildDate returns No Data Held when any blank input`(
      yearInput: String?,
      monthInput: String?,
      dayInput: String?,
    ) {
      val response = templateHelpers.buildDate(yearInput, monthInput, dayInput)
      assertThat(response).isEqualTo("No Data Held")
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "year     | 11    | 19        | year-11-19",
        "2024     | month | 19        | 2024-month-19",
        "2024     | 11    | something | 2024-11-something",
        "invalid  | date  | vals      | invalid-date-vals",
      ],
      delimiterString = "|",
    )
    fun `buildDate returns original values when not able to convert to date`(
      yearInput: String?,
      monthInput: String?,
      dayInput: String?,
      expectedValue: String,
    ) {
      val response = templateHelpers.buildDate(yearInput, monthInput, dayInput)
      assertThat(response).isEqualTo(expectedValue)
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "2024 | 11    | 19        | 19 November 2024",
        "2024 | 4     | 8         | 08 April 2024",
        "2024 | 04    | 08        | 08 April 2024",
      ],
      delimiterString = "|",
    )
    fun `buildDate returns formatted date`(
      yearInput: String?,
      monthInput: String?,
      dayInput: String?,
      expectedValue: String,
    ) {
      val response = templateHelpers.buildDate(yearInput, monthInput, dayInput)
      assertThat(response).isEqualTo(expectedValue)
    }
  }

  @Nested
  inner class BuildDateNumberTest {
    @ParameterizedTest
    @CsvSource(
      value = [
        "2024 | 11    | null",
        "2024 | null  | 15",
        "null | 11    | 15",
        "null | null  | null",
      ],
      delimiterString = "|",
      nullValues = ["null"],
    )
    fun `buildDateNumber returns No Data Held when any blank input`(
      yearInput: Int?,
      monthInput: Int?,
      dayInput: Int?,
    ) {
      val response = templateHelpers.buildDateNumber(yearInput, monthInput, dayInput)
      assertThat(response).isEqualTo("No Data Held")
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "-102 | 11  | 19  | -102-11-19",
        "2024 | -11 | 19  | 2024--11-19",
        "2024 | 11  | -19 | 2024-11--19",
      ],
      delimiterString = "|",
    )
    fun `buildDateNumber returns original values when not able to convert to date`(
      yearInput: Int?,
      monthInput: Int?,
      dayInput: Int?,
      expectedValue: String,
    ) {
      val response = templateHelpers.buildDateNumber(yearInput, monthInput, dayInput)
      assertThat(response).isEqualTo(expectedValue)
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "2024     | 11     | 19     | 19 November 2024",
        "2024     | 4      | 8      | 08 April 2024",
        "2024     | 04     | 08     | 08 April 2024",
        "2024.0   | 04.0   | 08.0   | 08 April 2024",
        "2024.434 | 04.563 | 08.544 | 08 April 2024",
      ],
      delimiterString = "|",
    )
    fun `buildDateNumber returns formatted date`(
      yearInput: Double?,
      monthInput: Double?,
      dayInput: Double?,
      expectedValue: String,
    ) {
      val response = templateHelpers.buildDateNumber(yearInput, monthInput, dayInput)
      assertThat(response).isEqualTo(expectedValue)
    }
  }

  @Nested
  inner class EqualsTest {
    @ParameterizedTest
    @CsvSource(
      value = [
        "Value 1 | Value 1",
        "null    | null",
      ],
      delimiterString = "|",
      nullValues = ["null"],
    )
    fun `eq returns true when args are equal`(firstArg: String?, secondArg: String?) {
      val response = templateHelpers.eq(firstArg, secondArg)
      assertThat(response).isTrue()
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "Value 1 | Value 2",
        "null    | Value 2",
        "Value 1 | null",
      ],
      delimiterString = "|",
      nullValues = ["null"],
    )
    fun `eq returns false when args are not equal`(firstArg: String?, secondArg: String?) {
      val response = templateHelpers.eq(firstArg, secondArg)
      assertThat(response).isFalse()
    }
  }

  @Nested
  inner class ConvertCamelCaseTest {
    @ParameterizedTest
    @CsvSource(
      value = [
        "camelCaseValue       | camel case value",
        "SomeValue            | some value",
        "Value With Spaces    | Value With Spaces",
        "                     | No Data Held",
        "null                 | No Data Held",
      ],
      delimiterString = "|",
      nullValues = ["null"],
    )
    fun `convertCamelCase returns expected value`(input: String?, expectedValue: String) {
      val response = templateHelpers.convertCamelCase(input)
      assertThat(response).isEqualTo(expectedValue)
    }
  }

  @Nested
  inner class OptionalStringTest {
    @ParameterizedTest
    @CsvSource(
      value = [
        "Data is Held       | Data is Held",
        "SOme random String | SOme random String",
        "''                 | No Data Held",
        "null               | No Data Held",
      ],
      delimiterString = "|",
      nullValues = ["null"],
    )
    fun `should return expected value`(input: String?, expectedValue: String) {
      assertThat(templateHelpers.optionalString(input)).isEqualTo(expectedValue)
    }

    @ParameterizedTest
    @MethodSource("uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpersTest#nonStringValues")
    fun `should throw expected exception if input is no String`(value: Any) {
      val actual = assertThrows<SubjectAccessRequestTemplatingException> {
        templateHelpers.optionalString(value)
      }
      assertThat(actual.message).startsWith("required type String or null, but actual type was ${value::class.simpleName}")
    }
  }

  companion object {
    @JvmStatic
    fun dateArrayValues(): Stream<Arguments> = Stream.of(
      Arguments.of(listOf(2023, 3, 24, 13, 59, 16, 133644), "24 March 2023, 1:59:16 pm"),
      Arguments.of(listOf(2023, 3, 24, 13, 59, 16), "24 March 2023, 1:59:16 pm"),
      Arguments.of(listOf(2023, 3, 24, 13, 59), "24 March 2023, 1:59 pm"),
      Arguments.of(listOf(2023, 3, 24, 13), "24 March 2023, 1:00 pm"),
      Arguments.of(listOf(2023, 3, 24), "24 March 2023, 12:00 am"),
      Arguments.of(listOf(2023, 3), "01 March 2023, 12:00 am"),
      Arguments.of(listOf(2023), "01 January 2023, 12:00 am"),
      Arguments.of(emptyList<Int>(), "01 January 0001, 12:00 am"),
      Arguments.of(listOf(2023.0, 3.0, 24.0, 13.0, 59.0, 16.0, 133644.0), "24 March 2023, 1:59:16 pm"),
      Arguments.of(listOf(2023.0, 3.0, 24.0, 13.0, 59.0, 16.0), "24 March 2023, 1:59:16 pm"),
      Arguments.of(listOf(2023.0, 3.0, 24.0, 13.0, 59.0), "24 March 2023, 1:59 pm"),
      Arguments.of(listOf(2023.0, 3.0, 24.0, 13.0), "24 March 2023, 1:00 pm"),
      Arguments.of(listOf(2023.0, 3.0, 24.0), "24 March 2023, 12:00 am"),
      Arguments.of(listOf(2023.0, 3.0), "01 March 2023, 12:00 am"),
      Arguments.of(listOf(2023.0), "01 January 2023, 12:00 am"),
      Arguments.of(listOf("2023", "3", "24", "13", "59", "16", "133644"), "24 March 2023, 1:59:16 pm"),
      Arguments.of(listOf("2023", "3", "24", "13", "59", "16"), "24 March 2023, 1:59:16 pm"),
      Arguments.of(listOf("2023", "3", "24", "13", "59"), "24 March 2023, 1:59 pm"),
      Arguments.of(listOf("2023", "3", "24", "13"), "24 March 2023, 1:00 pm"),
      Arguments.of(listOf("2023", "3", "24"), "24 March 2023, 12:00 am"),
      Arguments.of(listOf("2023", "3"), "01 March 2023, 12:00 am"),
      Arguments.of(listOf("2023"), "01 January 2023, 12:00 am"),
    )

    @JvmStatic
    fun nonStringValues(): List<Any> = listOf(
      99,
      10.0,
      mutableMapOf("A" to "B"),
      object {
        val name: String = "Homer Simpson"
      },
      true,
      false,
      listOf("1", "2", "3"),
    )
  }
}
