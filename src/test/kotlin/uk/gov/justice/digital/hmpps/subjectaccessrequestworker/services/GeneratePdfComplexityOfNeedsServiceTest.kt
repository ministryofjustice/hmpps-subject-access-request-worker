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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfComplexityOfNeedsServiceTest {

  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Complexity of Need Service`() {
    val serviceList = listOf(DpsService(name = "hmpps-complexity-of-need", content = complexityOfNeedServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-template-con.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()

    val reader = PdfDocument(PdfReader("dummy-template-con.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))
    assertThat(text).contains("Complexity of need")
  }

  private val complexityOfNeedServiceData = arrayListOf(
    mapOf(
      "offenderNo" to "A1234AA",
      "level" to "low",
      "sourceSystem" to "keyworker-to-complexity-api-test",
      "sourceUser" to "AUSER_GEN",
      "notes" to "string",
      "createdTimeStamp" to "2021-03-30T11:45:10.266Z",
      "active" to true,
    ),
    mapOf(
      "offenderNo" to "A1234AA",
      "level" to "low",
      "sourceSystem" to "keyworker-to-complexity-api-test",
      "sourceUser" to "AUSER_GEN",
      "notes" to "string",
      "createdTimeStamp" to "2021-03-30T19:54:46.056Z",
      "active" to true,
    ),
  )
}
