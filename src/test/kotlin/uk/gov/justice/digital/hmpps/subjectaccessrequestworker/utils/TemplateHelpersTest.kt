package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository

class TemplateHelpersTest {

  private val dateConversionHelper = DateConversionHelper
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository)

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
    fun `formatDate returns formatted date for valid input`() {
      val response = templateHelpers.formatDate("2023-10-01")
      assertThat(response).isEqualTo("01 October 2023")
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

    @Test
    fun `getUserLastName returns No Data Held if null`() {
      val response = templateHelpers.getUserLastName("")
      assertThat(response).isEqualTo("No Data Held")
    }
  }
}
