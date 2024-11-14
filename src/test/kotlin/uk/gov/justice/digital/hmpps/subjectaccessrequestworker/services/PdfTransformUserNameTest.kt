package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class PdfTransformUserNameTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for prison Name when caseload id supplied`() {
    whenever(userDetailsRepository.findByUsername("AZ123OP")).thenReturn(UserDetail("AZ123OP", "Basil"))
    whenever(userDetailsRepository.findByUsername("BB123LM")).thenReturn(UserDetail("BB123LM", "Reacher"))

    val serviceList = listOf(DpsService(name = "replace-username", content = serviceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-username-template.pdf")))
    val document = Document(pdfDocument)

    generatePdfService.addData(pdfDocument, document, serviceList)

    document.close()
    val reader = PdfDocument(PdfReader("dummy-username-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("User PDF")
    assertThat(text).contains("optionalValue")
    assertThat(text).contains("getUserLastName")
    assertThat(text).contains("Reacher")

    // Check that the user_id is  present in the pdf when the user_id is not present in the user repository
    assertThat(text).contains("XD888XT")

    verify(userDetailsRepository, times(3)).findByUsername(anyString())
    verifyNoInteractions(prisonDetailsRepository)
  }

  private val serviceData: Map<Any, Any> = mapOf(
    "firstUsername" to mapOf(
      "user_id" to "AZ123OP",
      "user_id_helper_value" to "optionalValue",
      "user_name_helper_value" to "getUserLastName",
    ),
    "secondUsername" to mapOf(
      "user_id" to "BB123LM",
      "user_id_helper_value" to "optionalValue",
      "user_name_helper_value" to "getUserLastName",
    ),
    "thirdUsername" to mapOf(
      "user_id" to "XD888XT",
      "user_id_helper_value" to "optionalValue",
      "user_name_helper_value" to "getUserLastName",
    ),
  )
}
