package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfIncentivesServiceTest {
  private val templateHelpers = TemplateHelpers()
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Incentives Service`() {
    val serviceList = listOf(DpsService(name = "hmpps-incentives-api", content = incentivesServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-incentives-template.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()
    val reader = PdfDocument(PdfReader("dummy-incentives-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("Incentives")
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
