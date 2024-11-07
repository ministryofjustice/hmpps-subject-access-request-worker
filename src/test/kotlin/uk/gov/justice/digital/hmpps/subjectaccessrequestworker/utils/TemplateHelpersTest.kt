package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TemplateHelpersTest {

  private val templateHelpers = TemplateHelpers()

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

  // todo test formatdate

  // todo test prisonFullName
}
