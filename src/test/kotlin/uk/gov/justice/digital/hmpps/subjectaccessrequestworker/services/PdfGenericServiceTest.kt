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
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class PdfGenericServiceTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for prison Name when `() {
    whenever(prisonDetailsRepository.findByPrisonId("HRI")).thenReturn(
      PrisonDetail(
        "HRI",
        "Haslar Immigration Removal Centre",
      ),
    )
    whenever(prisonDetailsRepository.findByPrisonId("MDI")).thenReturn(PrisonDetail("MDI", "Moorland (HMP & YOI)"))

    val serviceList = listOf(DpsService(name = "replace-prison-id-service", content = serviceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-generic-template.pdf")))
    val document = Document(pdfDocument)

    generatePdfService.addData(pdfDocument, document, serviceList)

    document.close()
    val reader = PdfDocument(PdfReader("dummy-generic-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("Prisoner Generic")
    assertThat(text).contains("optionalValue")
    assertThat(text).contains("getPrisonName")
    assertThat(text).contains("Haslar Immigration")
    assertThat(text).contains("Removal Centre")
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
