package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfServiceGTest : BaseGeneratePdfTest() {

  @ParameterizedTest
  @CsvSource(
    value = [
      "G1 | 'G1\nData is held'",
      "G2 | 'G2\nData is held'",
      "G3 | 'G3\nData is held'",
    ],
    delimiterString = "|",
  )
  fun `should generate expected PDF for service Gn when data is held`(
    serviceName: String,
    expectedPdfContent: String,
  ) {
    executeTest(
      serviceName = serviceName,
      expectedContent = expectedPdfContent,
      content = mapOf(
        "data" to "Data is held",
      ),
    )
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "G1 | 'G1\nNo Data Held'",
      "G2 | 'G2\nNo Data Held'",
      "G3 | 'G3\nNo Data Held'",
    ],
    delimiterString = "|",
  )
  fun `should generate expected PDF for service Gn when data is not held`(
    serviceName: String,
    expectedPdfContent: String,
  ) {
    executeTest(
      serviceName = serviceName,
      expectedContent = expectedPdfContent,
      content = "Date Not Held",
    )
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "G1 | 'G1\nData is held'",
      "G2 | 'G2\nData is held'",
      "G3 | 'G3\nData is held'",
    ],
    delimiterString = "|",
  )
  fun `should generate PDF with 'Data is held' only, all other content is ignored`(
    serviceName: String,
    expectedPdfContent: String,
  ) {
    executeTest(
      serviceName = serviceName,
      expectedContent = expectedPdfContent,
      content = mapOf(
        "data" to "Data is held",
        "sensitiveInformation" to "some sensitive information that should be hidden",
        "nestedField" to object {
          var name: String = "Bob"
        },
      ),
    )
  }

  private fun executeTest(serviceName: String, content: Any, expectedContent: String) {
    generateSubjectAccessRequestPdf(
      filename = "$serviceName-template.pdf",
      serviceList = listOf(DpsService(name = serviceName, content = content)),
    )

    getGeneratedPdfDocument("$serviceName-template.pdf").use { doc ->
      val text = PdfTextExtractor.getTextFromPage(doc.getPage(2))
      assertThat(text).isEqualTo(expectedContent)
    }
  }
}
