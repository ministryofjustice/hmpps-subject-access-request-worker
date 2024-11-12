package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository

class TemplateHelpersTest {

  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository)

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

  // todo test formatdate

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
}
