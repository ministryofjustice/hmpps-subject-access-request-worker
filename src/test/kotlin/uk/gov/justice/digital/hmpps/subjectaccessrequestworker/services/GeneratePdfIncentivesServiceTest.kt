package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfIncentivesServiceTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders for Incentives Service`() {
    val serviceList = listOf(DpsService(name = "hmpps-incentives-api", content = incentivesServiceData))
    generateSubjectAccessRequestPdf("dummy-incentives-template.pdf", serviceList)

    getGeneratedPdfDocument("dummy-incentives-template.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(2))
      assertThat(text).contains("Incentives")
    }
  }

  private val incentivesServiceData: ArrayList<Any> = arrayListOf(
    mapOf(
      "id" to 2898970,
      "bookingId" to "1208204",
      "prisonerNumber" to "A485634",
      "nextReviewDate" to "2019-12-03",
      "levelCode" to "ENH",
      "prisonId" to "UAL",
      "locationId" to "M-16-15",
      "reviewTime" to "2023-07-03T21:14:25.059172",
      "reviewedBy" to "MDI",
      "commentText" to "comment",
      "current" to true,
      "reviewType" to "REVIEW",
    ),
    mapOf(
      "id" to 2898971,
      "bookingId" to "4028021",
      "prisonerNumber" to "A1234AA",
      "nextReviewDate" to "2020-12-03",
      "levelCode" to "ENH",
      "prisonId" to "UAL",
      "locationId" to "M-16-15",
      "reviewTime" to "2023-07-03T21:14:25.059172",
      "reviewedBy" to "MDI",
      "commentText" to "comment",
      "current" to true,
      "reviewType" to "REVIEW",
    ),
  )
}
