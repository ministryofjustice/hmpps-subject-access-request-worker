package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfLaunchPadServiceTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders for Launchpad Auth Service`() {
    val serviceList = listOf(DpsService(name = "launchpad-auth", content = launchPadServiceData))
    generateSubjectAccessRequestPdf("dummy-template-launchpad-auth.pdf", serviceList)

    getGeneratedPdfDocument("dummy-template-launchpad-auth.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(2))
      assertThat(text).contains("Launchpad")
    }
  }

  private val launchPadServiceData = arrayListOf(
    mapOf(
      "id" to "6ccb67e3-5f05-4b73-a4d9-5a012541c424",
      "name" to "Launchpad Homepage",
      "firstLogInDate" to "2024-08-12T07:18:41Z",
      "lastLogInDate" to "2024-08-12T07:18:41Z",
      "permissionsGranted" to arrayListOf(
        mapOf(
          "humanReadable" to "Read basic information like your name",
        ),
        mapOf(
          "humanReadable" to "Read the list of applications you use",
        ),
        mapOf(
          "humanReadable" to "Remove access to applications you use",
        ),
        mapOf(
          "humanReadable" to "Read prison information like the name of your prison",
        ),
      ),
    ),
    mapOf(
      "id" to "6ccb67e3-5f05-4b73-a4d9-5a012541c425",
      "name" to "Launchpad Homepage",
      "firstLogInDate" to "2024-09-12T07:18:41Z",
      "lastLogInDate" to "2024-09-12T07:18:41Z",
      "permissionsGranted" to arrayListOf(
        mapOf(
          "humanReadable" to "Read basic information like your name",
        ),
        mapOf(
          "humanReadable" to "Read the list of applications you use",
        ),
        mapOf(
          "humanReadable" to "Remove access to applications you use",
        ),
        mapOf(
          "humanReadable" to "Read prison information like the name of your prison",
        ),
      ),
    ),
  )
}
