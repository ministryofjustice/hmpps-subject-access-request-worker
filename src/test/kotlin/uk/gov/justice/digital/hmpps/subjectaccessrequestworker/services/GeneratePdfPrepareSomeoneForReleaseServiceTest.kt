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

class GeneratePdfPrepareSomeoneForReleaseServiceTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Prepare Someone for Release service`() {
    val serviceList =
      listOf(DpsService(name = "hmpps-resettlement-passport-api", content = prepareSomeoneForReleaseServiceData))

    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-resettlement-template.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()

    val reader = PdfDocument(PdfReader("dummy-resettlement-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("Prepare Someone for Release")
  }

  private val prepareSomeoneForReleaseServiceData: Map<Any, Any> = mapOf(
    "prisoner" to mapOf(
      "id" to 3,
      "nomsId" to "A8731DY",
      "creationDate" to "2023-11-17T14:49:58.308566",
      "crn" to "U328968",
      "prisonId" to "MDI",
      "releaseDate" to "2024-09-17",
    ),
    "assessment" to mapOf(
      "id" to 518,
      "prisonerId" to 3,
      "creationDate" to "2024-03-19T15:32:57.283459",
      "assessmentDate" to "2023-01-08T00:00:00",
      "isBankAccountRequired" to false,
      "isIdRequired" to true,
      "isDeleted" to false,
      "deletionDate" to null,
      "idDocuments" to arrayListOf(
        mapOf(
          "id" to 8,
          "name" to "Deed poll certificate",
        ),
        mapOf(
          "id" to 2,
          "name" to "Marriage certificate",
        ),
      ),
    ),
    "bankApplication" to mapOf(
      "id" to 1537,
      "applicationSubmittedDate" to "2023-12-01T00:00:00",
      "currentStatus" to "Account opened",
      "bankName" to "Co-op",
      "bankResponseDate" to "2023-12-12T00:00:00",
      "isAddedToPersonalItems" to true,
      "addedToPersonalItemsDate" to "2023-12-12T00:00:00",
      "prisoner" to mapOf(
        "id" to 3,
        "nomsId" to "A8731DY",
        "creationDate" to "2023-11-17T14:49:58.308566",
        "crn" to "U328968",
        "prisonId" to "MDI",
        "releaseDate" to "2024-09-17",
      ),
      "logs" to arrayListOf(
        mapOf(
          "id" to 3302,
          "status" to "Pending",
          "changeDate" to "2023-12-01T00:00:00",
        ),
        mapOf(
          "id" to 3303,
          "status" to "Account opened",
          "changeDate" to "2023-12-04T00:00:00",
        ),
      ),
    ),
    "deliusContact" to arrayListOf(
      mapOf(
        "caseNoteId" to "db-2",
        "pathway" to "FINANCE_AND_ID",
        "creationDateTime" to "2023-12-13T12:33:30.514175",
        "occurenceDateTime" to "2023-12-13T12:33:30.514175",
        "createdBy" to "James Boobier",
        "text" to "Resettlement status set to: Support not required. This is a case note from Delius",
      ),
      mapOf(
        "caseNoteId" to "db-3",
        "pathway" to "FINANCE_AND_ID",
        "creationDateTime" to "2023-12-13T12:33:30.514175",
        "occurenceDateTime" to "2023-12-13T12:33:30.514175",
        "createdBy" to "James Boobier",
        "text" to "Resettlement status set to: Done. This is a case note from Delius",
      ),
    ),
    "idApplication" to mapOf(
      "idType" to mapOf(
        "id" to 6,
        "name" to "Driving licence",
      ),
      "creationDate" to "2024-05-01T11:12:32.681477",
      "applicationSubmittedDate" to "2024-05-01T00:00:00",
      "isPriorityApplication" to false,
      "costOfApplication" to 100,
      "refundAmount" to 100,
      "haveGro" to null,
      "isUkNationalBornOverseas" to null,
      "countryBornIn" to null,
      "caseNumber" to null,
      "courtDetails" to null,
      "driversLicenceType" to "Renewal",
      "driversLicenceApplicationMadeAt" to "Online",
      "isAddedToPersonalItems" to null,
      "addedToPersonalItemsDate" to null,
      "status" to "Rejected",
      "statusUpdateDate" to "2024-05-01T12:43:56.722624",
      "isDeleted" to false,
      "deletionDate" to null,
      "dateIdReceived" to null,
      "id" to 2148,
      "prisonerId" to 3,
    ),
    "statusSummary" to arrayListOf(
      mapOf(
        "type" to "BCST2",
        "pathwayStatus" to arrayListOf(
          mapOf(
            "pathway" to "ACCOMMODATION",
            "assessmentStatus" to "SUBMITTED",
          ),
          mapOf(
            "pathway" to "DRUGS_AND_ALCOHOL",
            "assessmentStatus" to "SUBMITTED",
          ),
        ),
      ),
    ),
    "resettlementAssessment" to arrayListOf(
      mapOf(
        "originalAssessment" to mapOf(
          "assessmentType" to "BCST2",
          "lastUpdated" to "2024-09-02T08:54:37.979749",
          "updatedBy" to "Nick Judge",
          "questionsAndAnswers" to arrayListOf(
            mapOf(
              "questionTitle" to "Where did the person in prison live before custody?",
              "answer" to "No answer provided",
              "originalPageId" to "ACCOMMODATION_REPORT",
            ),
            mapOf(
              "questionTitle" to "Support needs?",
              "answer" to "None",
              "originalPageId" to "SUPPORT_REQUIREMENTS",
            ),
          ),
        ),
        "latestAssessment" to mapOf(
          "assessmentType" to "RESETTLEMENT_PLAN",
          "lastUpdated" to "2024-09-02T08:54:37.979749",
          "updatedBy" to "James Boobier",
          "questionsAndAnswers" to arrayListOf(
            mapOf(
              "questionTitle" to "Where did the person in prison live before custody?",
              "answer" to "No answer provided",
              "originalPageId" to "ACCOMMODATION_REPORT",
            ),
            mapOf(
              "questionTitle" to "Support needs?",
              "answer" to "Help finding accomodation",
              "originalPageId" to "SUPPORT_REQUIREMENTS",
            ),
          ),
        ),
      ),
    ),
  )
}
