package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail

class PdfTransformPrisonNameTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders for prison Name when caseload id supplied`() {
    whenever(prisonDetailsRepository.findByPrisonId("HRI")).thenReturn(
      PrisonDetail(
        "HRI",
        "Haslar Immigration Removal Centre",
      ),
    )
    whenever(prisonDetailsRepository.findByPrisonId("MDI")).thenReturn(PrisonDetail("MDI", "Moorland (HMP & YOI)"))

    val serviceList = listOf(DpsService(name = "replace-prison-id-service", content = serviceData))
    generateSubjectAccessRequestPdf("dummy-prison-id-template.pdf", serviceList)

    getGeneratedPdfDocument("dummy-prison-id-template.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(2))

      assertThat(text).contains("Prisoner Generic")
      assertThat(text).contains("optionalValue")
      assertThat(text).contains("getPrisonName")
      assertThat(text).contains("Haslar Immigration")
      assertThat(text).contains("Removal Centre")

      verify(prisonDetailsRepository, times(1)).findByPrisonId("MDI")
      verify(prisonDetailsRepository, times(1)).findByPrisonId("HRI")
      verifyNoMoreInteractions(prisonDetailsRepository)
    }
  }

  private val serviceData: Map<Any, Any> = mapOf(
    "prisonId" to mapOf(
      "prison_id" to "HRI",
      "prison_id_helper_value" to "optionalValue",
      "prison_name_helper_value" to "getPrisonName",
    ),
    "secondPrisonId" to mapOf(
      "prison_id" to "MDI",
      "prison_id_helper_value" to "optionalValue",
      "prison_name_helper_value" to "getPrisonName",
    ),
  )
}
