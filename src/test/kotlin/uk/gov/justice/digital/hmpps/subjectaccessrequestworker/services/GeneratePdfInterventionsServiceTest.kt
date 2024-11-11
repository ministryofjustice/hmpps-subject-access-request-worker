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

class GeneratePdfInterventionsServiceTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Interventions Service`() {
    val serviceList = listOf(DpsService(name = "hmpps-interventions-service", content = interventionsServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-interventions-service-template.pdf")))
    val document = Document(pdfDocument)

    generatePdfService.addData(pdfDocument, document, serviceList)

    document.close()
    val reader = PdfDocument(PdfReader("dummy-interventions-service-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("Refer and monitor an intervention")
  }

  private val interventionsServiceData: Map<Any, Any> = mapOf(
    "crn" to "X718253",
    "referral" to arrayListOf(
      mapOf(
        "referral_number" to "JE2862AC",
        "accessibility_needs" to "SAR test 9 - Does Sadie have any other mobility, disability or accessibility needs? (optional)\r\n - None",
        "additional_needs_information" to "SAR test 10 - Additional information about Sadie’s needs (optional) - None",
        "when_unavailable" to "SAR test 11 - Provide details of when Sadie will not be able to attend sessions - Weekday mornings",
        "end_requested_comments" to "",
        "appointment" to arrayListOf(
          mapOf(
            "session_summary" to "SAR Test 22 - What did you do in the session?",
            "session_response" to "SAR Test 23 - How did Sadie Borer respond to the session?",
            "session_concerns" to "SAR Test 25 - Yes, something concerned me about Sadie Borer",
            "late_reason" to "SAR Test 21 - Add how late they were and anything you know about the reason.",
            "future_session_plan" to "SAR Test 26 - Add anything you have planned for the next session (optional)",
          ),
          mapOf(
            "session_summary" to "SAR 27 - What did you do in the session?",
            "session_response" to "SAR 28 - How did Sadie Borer respond to the session?",
            "session_concerns" to "SAR 30 - Yes, something concerned me about Sadie Borer",
            "late_reason" to "",
            "future_session_plan" to "SAR 31 - Add anything you have planned for the next session (optional)",
          ),
        ),
        "action_plan_activity" to arrayListOf(
          mapOf(
            "description" to arrayListOf(
              "SAR Test 19 - Please write the details of the activity here.",
              "SAR Test 20 - Activity 2 - Please write the details of the activity here.",
            ),
          ),
          mapOf(
            "description" to arrayListOf(
              "example",
            ),
          ),
        ),
        "end_of_service_report" to mapOf(
          "end_of_service_outcomes" to arrayListOf(
            mapOf(
              "progression_comments" to "SAR Test 32 - Describe their progress on this outcome.",
              "additional_task_comments" to "SAR Test 33 - Enter if anything else needs to be done (optional)",
            ),
            mapOf(
              "progression_comments" to "test.",
              "additional_task_comments" to "test",
            ),
          ),
        ),
      ),
      mapOf(
        "referral_number" to "FY7705FI",
        "accessibility_needs" to "mobility",
        "additional_needs_information" to "",
        "when_unavailable" to "Fridays",
        "end_requested_comments" to "",
        "appointment" to emptyList<Any>(),
        "action_plan_activity" to emptyList<Any>(),
        "end_of_service_report" to null,
      ),
    ),
  )
}
