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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfCategorisationServiceTest {
  private val templateHelpers = TemplateHelpers()
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Categorisation Service`() {
    val serviceList = listOf(DpsService(name = "hmpps-offender-categorisation-api", content = categoryServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-categorisation-api-template.pdf")))
    val document = Document(pdfDocument)

    generatePdfService.addData(pdfDocument, document, serviceList)

    document.close()
    val reader = PdfDocument(PdfReader("dummy-categorisation-api-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("Prisoner categorisation")
  }

  private val categoryServiceData: Map<Any, Any> = mapOf(
    "categorisationTool" to mapOf(
      "catForm" to mapOf(
        "form_response" to mapOf(
          "ratings" to mapOf(
            "escapeRating" to mapOf(
              "escapeCatB" to "Yes",
              "escapeCatBText" to "escape cat b text",
              "escapeOtherEvidence" to "Yes",
              "escapeOtherEvidenceText" to "escape other evidence text",
            ),
            "extremismRating" to mapOf(
              "previousTerrorismOffences" to "Yes",
              "previousTerrorismOffencesText" to "previous terrorism offences text - talking about bombs",
            ),
            "furtherCharges" to mapOf(
              "furtherCharges" to "Yes",
              "furtherChargesCatB" to "Yes",
              "furtherChargesText" to "further charges text",
            ),
            "violenceRating" to mapOf(
              "seriousThreat" to "Yes",
              "seriousThreatText" to "serious threat text",
              "highRiskOfViolence" to "Yes",
              "highRiskOfViolenceText" to "high risk of violence text",
            ),
            "offendingHistory" to mapOf(
              "previousConvictions" to "No",
            ),
            "securityInput" to mapOf(
              "securityInputNeeded" to "Yes",
              "securityInputNeededText" to "Test",
            ),
            "securityBack" to mapOf(
              "catB" to "Yes",
            ),
            "decision" to mapOf(
              "category" to "Test",
            ),
          ),
        ),
        // not included - system ID:
        "booking_id" to "832899",
        "status" to "STARTED",
        "referred_date" to "30-12-2020",
        // not included - system ID:
        "sequence_no" to "1",
        "risk_profile" to mapOf(
          "lifeProfile" to mapOf(
            "life" to true,
            // not included - duplicate ID:
            "nomsId" to "example",
            "riskType" to "example",
            "provisionalCategorisation" to "example",
          ),
          "escapeProfile" to mapOf(
            // not included - duplicate ID:
            "nomsId" to "example",
            "riskType" to "example",
            "provisionalCategorisation" to "example",
          ),
          "violenceProfile" to mapOf(
            // not included - duplicate ID:
            "nomsId" to "example",
            "riskType" to "example",
            "displayAssaults" to true,
            "numberOfAssaults" to 2,
            "notifySafetyCustodyLead" to true,
            "numberOfSeriousAssaults" to 1,
            "numberOfNonSeriousAssaults" to 1,
            "veryHighRiskViolentOffender" to true,
            "provisionalCategorisation" to "example",
          ),
        ),
        "prison_id" to "MDI",
        // not included - duplicate ID:
        "offender_no" to "G2515UU",
        "start_date" to "2024-05-22 10:45:22.627786+01",
        "cat_type" to "INITIAL",
        "review_reason" to "MANUAL",
        "due_by_date" to "2014-06-16",
        "cancelled_date" to "exampleDate",
      ),
      "liteCategory" to mapOf(
        "category" to "U",
        "supervisorCategory" to "U",
        // not included - duplicate ID:
        "offender_no" to "G0552UV",
        // not included - duplicate ID:
        "prison_id" to "MDI",
        "created_date" to "2021-05-04T06:58:12.399139Z",
        "approved_date" to "2021-05-04T00:00Z",
        "assessment_committee" to "OCA",
        "assessment_comment" to "steve test 677",
        "next_review_date" to "2021-06-04",
        "placement_prison_id" to "",
        "approved_committee" to "OCA",
        "approved_placement_prison_id" to "",
        "approved_placement_comment" to "",
        "approved_comment" to "steve test 677",
        // not included - system ID:
        "sequence" to "15",
      ),
    ),
    "riskProfiler" to mapOf(
      // not included - system ID:
      "offender_no" to "G2515UU",
      "violence" to mapOf(
        // not included - duplicate ID:
        "nomsId" to "G2515UU",
        "riskType" to "VIOLENCE",
        "displayAssaults" to true,
        "numberOfAssaults" to 4,
        "notifySafetyCustodyLead" to false,
        "numberOfSeriousAssaults" to 0,
        "numberOfNonSeriousAssaults" to 0,
        "provisionalCategorisation" to "C",
        "veryHighRiskViolentOffender" to false,
      ),
      "execute_date_time" to "2021-07-27T02:17:48.130833Z",
    ),
  )
}
