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

class GeneratePdfAdjudicationServiceTest {
  private val templateHelpers = TemplateHelpers()
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService  renders for Adjudications service`() {
    val serviceList = listOf(DpsService(name = "hmpps-manage-adjudications-api", content = testAdjudicationsServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-template-adjudications.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()
    val reader = PdfDocument(PdfReader("dummy-template-adjudications.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))
    assertThat(text).contains("Manage Adjudications")
  }

  private val testAdjudicationsServiceData: ArrayList<Any> =
    arrayListOf(
      mapOf(
        "chargeNumber" to "1525733",
        "prisonerNumber" to "A3863DZ",
        "gender" to "FEMALE",
        "incidentDetails" to mapOf(
          "locationId" to 26149,
          "dateTimeOfIncident" to "2023-06-08T12:00:00",
          "dateTimeOfDiscovery" to "2023-06-08T12:00:00",
          "handoverDeadline" to "2023-06-10T12:00:00",
        ),
        "isYouthOffender" to false,
        "incidentRole" to mapOf(
          "roleCode" to "25c",
          "offenceRule" to mapOf(
            "paragraphNumber" to "25(c)",
            "paragraphDescription" to "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:",
          ),
          "associatedPrisonersNumber" to "A3864DZ",
        ),
        "offenceDetails" to mapOf(
          "offenceCode" to 16001,
          "offenceRule" to mapOf(
            "paragraphNumber" to "16",
            "paragraphDescription" to "Intentionally or recklessly sets fire to any part of a prison or any other property, whether or not her own",
            "nomisCode" to "51:16",
            "withOthersNomisCode" to "51:25C",
          ),
          "protectedCharacteristics" to mapOf(
            "id" to 247,
            "characteristic" to "AGE",
          ),
        ),
        "incidentStatement" to mapOf(
          "statement" to "Vera incited Brian Duckworth to set fire to a lamp\r\ndamages - the lamp\r\nevidence includes something in a bag with a reference number of 1234\r\nwitnessed by amarktest",
          "completed" to true,
        ),
        "createdByUserId" to "USERA_GEN",
        "createdDateTime" to "2023-06-08T14:17:20.831884",
        "status" to "CHARGE_PROVED",
        "reviewedByUserId" to "USERB_GEN",
        "statusReason" to "",
        "statusDetails" to "",
        "damages" to arrayListOf(
          mapOf(
            "code" to "ELECTRICAL_REPAIR",
            "details" to "mend a lamp",
            "reporter" to "USERA_GEN",
          ),
        ),
        "evidence" to arrayListOf(
          mapOf(
            "code" to "BAGGED_AND_TAGGED",
            "identifier" to "1234",
            "details" to "evidence in a bag with a reference number",
            "reporter" to "USERA_GEN",
          ),
        ),
        "witnesses" to arrayListOf(
          mapOf(
            "code" to "OFFICER",
            "firstName" to "User",
            "lastName" to "A",
            "reporter" to "USERA_GEN",
          ),
        ),
        "hearings" to arrayListOf(
          mapOf(
            "id" to 467,
            "locationId" to 775,
            "dateTimeOfHearing" to "2023-06-08T14:25:00",
            "oicHearingType" to "INAD_ADULT",
            "outcome" to mapOf(
              "id" to 534,
              "adjudicator" to "James Peach",
              "code" to "COMPLETE",
              "plea" to "GUILTY",
            ),
            "agencyId" to "MDI",
          ),
        ),
        "disIssueHistory" to arrayListOf(
          mapOf(
            "issuingOfficer" to "someone",
            "dateTimeOfIssue" to "2023-06-08T14:25:00",
          ),
        ),
        "dateTimeOfFirstHearing" to "2023-06-08T14:25:00",
        "outcomes" to arrayListOf(
          mapOf(
            "hearing" to mapOf(
              "id" to 467,
              "locationId" to 775,
              "dateTimeOfHearing" to "2023-06-08T14:25:00",
              "oicHearingType" to "INAD_ADULT",
              "outcome" to mapOf(
                "id" to 534,
                "adjudicator" to "James Peach",
                "code" to "COMPLETE",
                "plea" to "GUILTY",
              ),
              "agencyId" to "MDI",
            ),
            "outcome" to mapOf(
              "outcome" to mapOf(
                "id" to 733,
                "code" to "CHARGE_PROVED",
                "canRemove" to true,
              ),
            ),
          ),
        ),
        "punishments" to arrayListOf(
          mapOf(
            "id" to 241,
            "type" to "PRIVILEGE",
            "privilegeType" to "TV",
            "schedule" to mapOf(
              "days" to 7,
              "duration" to 7,
              "measurement" to "DAYS",
              "startDate" to "2023-06-09",
              "endDate" to "2023-06-16",
            ),
            "canRemove" to true,
            "canEdit" to true,
            "rehabilitativeActivities" to arrayListOf(
              mapOf(
                "id" to 241,
                "details" to "Some info",
                "monitor" to "yes",
                "endDate" to "2023-06-09",
                "totalSessions" to 16,
                "completed" to true,
              ),
            ),
          ),
          mapOf(
            "id" to 240,
            "type" to "DAMAGES_OWED",
            "schedule" to mapOf(
              "days" to 0,
              "duration" to 0,
              "measurement" to "DAYS",
            ),
            "damagesOwedAmount" to 20,
            "canRemove" to true,
            "canEdit" to true,
            "rehabilitativeActivities" to emptyList<Any>(),
          ),
        ),
        "punishmentComments" to mapOf(
          "id" to 1,
          "comment" to "test comment",
          "reasonForChange" to "APPEAL",
          "nomisCreatedBy" to "person",
          "actualCreatedDate" to "2023-06-16",
        ),
        "outcomeEnteredInNomis" to false,
        "originatingAgencyId" to "MDI",
        "linkedChargeNumbers" to arrayListOf("9872-1", "9872-2"),
        "canActionFromHistory" to false,
      ),
    )
}
