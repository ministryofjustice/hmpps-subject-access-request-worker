package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfComplexityOfNeedsServiceTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders for Complexity of Need Service`() {
    val serviceList = listOf(DpsService(name = "hmpps-complexity-of-need", content = complexityOfNeedServiceData))
    generateSubjectAccessRequestPdf("dummy-template-con.pdf", serviceList)

    getGeneratedPdfDocument("dummy-template-con.pdf").use { doc ->
      val text = PdfTextExtractor.getTextFromPage(doc.getPage(2))
      assertThat(text).contains("Complexity of need")
    }
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
