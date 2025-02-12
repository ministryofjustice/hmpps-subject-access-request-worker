package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail

class PdfTransformUserNameTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders for prison Name when caseload id supplied`() {
    whenever(userDetailsRepository.findByUsername("AZ123OP")).thenReturn(UserDetail("AZ123OP", "Basil"))
    whenever(userDetailsRepository.findByUsername("BB123LM")).thenReturn(UserDetail("BB123LM", "Reacher"))

    val serviceList = listOf(DpsService(name = "replace-username", content = serviceData))
    generateSubjectAccessRequestPdf("dummy-username-template.pdf", serviceList)

    getGeneratedPdfDocument("dummy-username-template.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(2))

      assertThat(text).contains("User PDF")
      assertThat(text).contains("optionalValue")
      assertThat(text).contains("getUserLastName")
      assertThat(text).contains("Reacher")

      // Check that the user_id is  present in the pdf when the user_id is not present in the user repository
      assertThat(text).contains("XD888XT")

      verify(userDetailsRepository, times(3)).findByUsername(anyString())
      verifyNoInteractions(prisonDetailsRepository)
    }
  }

  private val serviceData: Map<Any, Any> = mapOf(
    "firstUsername" to mapOf(
      "user_id" to "AZ123OP",
      "user_id_helper_value" to "optionalValue",
      "user_name_helper_value" to "getUserLastName",
    ),
    "secondUsername" to mapOf(
      "user_id" to "BB123LM",
      "user_id_helper_value" to "optionalValue",
      "user_name_helper_value" to "getUserLastName",
    ),
    "thirdUsername" to mapOf(
      "user_id" to "XD888XT",
      "user_id_helper_value" to "optionalValue",
      "user_name_helper_value" to "getUserLastName",
    ),
  )
}
