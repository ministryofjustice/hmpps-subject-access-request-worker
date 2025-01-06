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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfKeyworkerServiceTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Keyworker Service`() {
    whenever(prisonDetailsRepository.findByPrisonId("MDI")).thenReturn(PrisonDetail("MDI", "Moorland (HMP & YOI)"))
    val serviceList = listOf(DpsService(name = "keyworker-api", content = testKeyworkerServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-keyworker-template.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()
    val reader = PdfDocument(PdfReader("dummy-keyworker-template.pdf"))
    val page = reader.getPage(2)
    val text = PdfTextExtractor.getTextFromPage(page)

    assertThat(text).contains("Keyworker")
    assertThat(text).contains("MARKE")
    assertThat(text).contains("Prison name")
    assertThat(text).contains("Moorland (HMP & YOI)")

    verify(prisonDetailsRepository, times(1)).findByPrisonId("MDI")
    verifyNoMoreInteractions(prisonDetailsRepository)
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
