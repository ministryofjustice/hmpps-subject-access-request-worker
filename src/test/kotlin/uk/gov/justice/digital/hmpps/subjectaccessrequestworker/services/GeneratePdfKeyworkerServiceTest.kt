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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail
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
    whenever(userDetailsRepository.findByUsername("AUSER_GEN")).thenReturn(UserDetail("AUSER_GEN", "Reacher"))
    val serviceList = listOf(DpsService(name = "keyworker-api", content = testKeyworkerServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-keyworker-template.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()
    val reader = PdfDocument(PdfReader("dummy-keyworker-template.pdf"))
    val page = reader.getPage(2)
    val text = PdfTextExtractor.getTextFromPage(page)

    assertThat(text).contains("Keyworker")
    assertThat(text).contains("Reacher")

    verify(userDetailsRepository, times(1)).findByUsername("AUSER_GEN")
    verifyNoMoreInteractions(userDetailsRepository)
  }

  private val testKeyworkerServiceData: ArrayList<Any> = arrayListOf(
    mapOf(
      "offenderKeyworkerId" to 12912,
      "offenderNo" to "A1234AA",
      "staffId" to 485634,
      "assignedDateTime" to "2019-12-03T11:00:58.21264",
      "active" to false,
      "allocationReason" to "MANUAL",
      "allocationType" to "M",
      "userId" to "AUSER_GEN",
      "prisonId" to "MDI",
      "expiryDateTime" to "2020-12-02T16:31:01",
      "deallocationReason" to "RELEASED",
      "creationDateTime" to "2019-12-03T11:00:58.213527",
      "createUserId" to "AUSER_GEN",
      "modifyDateTime" to "2020-12-02T16:31:32.128317",
      "modifyUserId" to "AUSER_GEN",
    ),
  )
}
