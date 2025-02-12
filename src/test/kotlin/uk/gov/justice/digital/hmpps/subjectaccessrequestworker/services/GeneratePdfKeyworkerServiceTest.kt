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

class GeneratePdfKeyworkerServiceTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders for Keyworker Service`() {
    whenever(prisonDetailsRepository.findByPrisonId("MDI"))
      .thenReturn(PrisonDetail("MDI", "Moorland (HMP & YOI)"))

    val serviceList = listOf(DpsService(name = "keyworker-api", content = testKeyworkerServiceData))
    generateSubjectAccessRequestPdf("dummy-keyworker-template.pdf", serviceList)

    getGeneratedPdfDocument("dummy-keyworker-template.pdf").use { pdf ->
      val page = pdf.getPage(2)
      val text = PdfTextExtractor.getTextFromPage(page)

      assertThat(text).contains("Keyworker")
      assertThat(text).contains("MARKE")
      assertThat(text).contains("Prison name")
      assertThat(text).contains("Moorland (HMP & YOI)")

      verify(prisonDetailsRepository, times(1)).findByPrisonId("MDI")
      verifyNoMoreInteractions(prisonDetailsRepository)
    }
  }

  private val testKeyworkerServiceData: ArrayList<Any> = arrayListOf(
    mapOf(
      "allocatedAt" to "2019-12-03T11:00:58.21264",
      "allocationExpiredAt" to "2020-12-02T16:31:01.21264",
      "activeAllocation" to false,
      "allocationReason" to "Manual",
      "allocationType" to "Manual",
      "keyworker" to mapOf(
        "firstName" to "ANDY",
        "lastName" to "MARKE",
      ),
      "prisonCode" to "MDI",
      "deallocationReason" to "Released",
    ),
  )
}
