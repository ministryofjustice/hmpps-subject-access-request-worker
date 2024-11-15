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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfLaunchPadServiceTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Launchpad Auth Service`() {
    val serviceList = listOf(DpsService(name = "launchpad-auth", content = launchPadServiceData))
    val writer = PdfWriter(FileOutputStream("dummy-template-launchpad-auth.pdf"))
    val pdfDocument = PdfDocument(writer)
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()

    val reader = PdfDocument(PdfReader("dummy-template-launchpad-auth.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))
    assertThat(text).contains("Launchpad")
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
