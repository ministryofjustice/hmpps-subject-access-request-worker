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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfAccreditedProgrammesServiceTest {

  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Accredited Programmes Service`() {
    whenever(userDetailsRepository.findByUsername("ADMINA_ADM")).thenReturn(UserDetail("ADMINA_ADM", "March-Phillips"))
    whenever(userDetailsRepository.findByUsername("USERA_GEN")).thenReturn(UserDetail("USERA_GEN", "Appleyard"))
    whenever(userDetailsRepository.findByUsername("USERC_GEN")).thenReturn(UserDetail("USERA_GEN", "Lassen"))
    val serviceList =
      listOf(DpsService(name = "hmpps-accredited-programmes-api", content = accreditedProgrammesServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-accredited-programmes-template.pdf")))
    val document = Document(pdfDocument)

    generatePdfService.addData(pdfDocument, document, serviceList)

    document.close()
    val reader = PdfDocument(PdfReader("dummy-accredited-programmes-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("Accredited programmes")
    assertThat(text).contains("Referral")
    assertThat(text).contains("Prisoner number")
    assertThat(text).contains("March-Phillips")
    assertThat(text).contains("Appleyard")
    assertThat(text).contains("Lassen")

    verify(userDetailsRepository, times(3)).findByUsername("ADMINA_ADM")
    verify(userDetailsRepository, times(1)).findByUsername("USERA_GEN")
    verify(userDetailsRepository, times(2)).findByUsername("USERC_GEN")
    verifyNoInteractions(prisonDetailsRepository)
  }

  private val accreditedProgrammesServiceData: Map<Any, Any> = mapOf(
    "referrals" to arrayListOf(
      mapOf(
        "prisonerNumber" to "A8610DY",
        "oasysConfirmed" to true,
        "statusCode" to "DESELECTED",
        "hasReviewedProgrammeHistory" to true,
        "additionalInformation" to "test",
        "submittedOn" to "2024-03-12T14:23:12.328775",
        "referrerUsername" to "ADMINA_ADM",
        "courseName" to "Becoming New Me Plus",
        "audience" to "Sexual offence",
        "courseOrganisation" to "WTI",
      ),
      mapOf(
        "prisonerNumber" to "A8610DY",
        "oasysConfirmed" to false,
        "statusCode" to "REFERRAL_STARTED",
        "hasReviewedProgrammeHistory" to false,
        "additionalInformation" to null,
        "submittedOn" to null,
        "referrerUsername" to "USERA_GEN",
        "courseName" to "Becoming New Me Plus",
        "audience" to "Intimate partner violence offence",
        "courseOrganisation" to "AYI",
      ),
    ),
    "courseParticipation" to arrayListOf(
      mapOf(
        "prisonerNumber" to "A8610DY",
        "yearStarted" to null,
        "source" to null,
        "type" to "CUSTODY",
        "outcomeStatus" to "COMPLETE",
        "yearCompleted" to 2020,
        "location" to null,
        "detail" to null,
        "courseName" to "Kaizen",
        "createdByUser" to "USERC_GEN",
        "createdDateTime" to "2024-07-12T14:57:42.431163",
        "updatedByUser" to "USERC_GEN",
        "updatedDateTime" to "2024-07-12T14:58:38.597915",
      ),
      mapOf(
        "prisonerNumber" to "A8610DY",
        "yearStarted" to 2002,
        "source" to "Example",
        "type" to "COMMUNITY",
        "outcomeStatus" to "COMPLETE",
        "yearCompleted" to 2004,
        "location" to "Example",
        "detail" to "Example",
        "courseName" to "Enhanced Thinking Skills",
        "createdByUser" to "ADMINA_ADM",
        "createdDateTime" to "2024-07-12T14:57:42.431163",
        "updatedByUser" to "ADMINA_ADM",
        "updatedDateTime" to "2024-07-12T14:58:38.597915",
      ),
    ),
  )
}
