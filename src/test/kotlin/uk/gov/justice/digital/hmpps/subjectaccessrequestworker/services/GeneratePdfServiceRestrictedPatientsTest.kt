package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail

class GeneratePdfServiceRestrictedPatientsTest : BaseGeneratePdfTest() {

  private fun writeAndThenReadPdf(
    testInput: Map<String, String>?,
  ): PdfDocument {
    val testFileName = "dummy-template-restricted-patients.pdf"
    val serviceList = listOf(DpsService(name = "hmpps-restricted-patients-api", content = testInput))
    generateSubjectAccessRequestPdf(testFileName, serviceList)

    return getGeneratedPdfDocument(testFileName)
  }

  @Test
  fun `generatePdfService renders for Restricted Patients API`() {
    val testInput = mapOf(
      "prisonerNumber" to "A1234AA",
      "supportingPrisonId" to "EXI",
      "hospitalLocationDescription" to "Weston Park Hospital",
      "dischargeTime" to "2024-09-05T08:50:44.19812",
    )
    whenever(prisonDetailsRepository.findByPrisonId("EXI")).thenReturn(PrisonDetail("EXI", "Exeter (HMP)"))

    writeAndThenReadPdf(testInput).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Restricted Patients")
      assertThat(text).contains("Discharge time 05 September 2024, 8:50:44 am")
      assertThat(text).contains("Hospital location Weston Park Hospital")
      assertThat(text).contains("Supporting prison Exeter (HMP)")
    }
  }

  @Test
  fun `generatePdfService renders for Restricted Patients API with optional data missing`() {
    val testInput = mapOf(
      "prisonerNumber" to "A1234AA",
      "hospitalLocationDescription" to "Weston Park Hospital",
      "dischargeTime" to "2024-09-05T08:50:44.19812",
    )
    writeAndThenReadPdf(testInput).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Restricted Patients")
      assertThat(text).contains("Discharge time 05 September 2024, 8:50:44 am")
      assertThat(text).contains("Hospital location Weston Park Hospital")
      assertThat(text).contains("Supporting prison No Data Held")
    }
  }

  @Test
  fun `generatePdfService renders for Restricted Patients API with no data held`() {
    writeAndThenReadPdf(null).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Restricted Patients")
      assertThat(text).doesNotContain("Prison number")
      assertThat(text).contains("No data held")
    }
  }
}
