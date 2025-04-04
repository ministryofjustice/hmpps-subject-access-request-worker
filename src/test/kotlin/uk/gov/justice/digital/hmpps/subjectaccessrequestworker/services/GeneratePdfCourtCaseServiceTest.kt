package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfCourtCaseServiceTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders for Court Case Service`() {
    val serviceList = listOf(DpsService(name = "court-case-service", content = courtCaseServiceData))
    generateSubjectAccessRequestPdf("dummy-template-court-case.pdf", serviceList)

    getGeneratedPdfDocument("dummy-template-court-case.pdf").use { doc ->
      val text = PdfTextExtractor.getTextFromPage(doc.getPage(2))
      assertThat(text).contains("Prepare a Case for Sentence")
    }
  }

  private val courtCaseServiceData = mapOf(
    "comments" to arrayListOf(
      mapOf(
        "comment" to "test",
        "author" to "Ravishankar Challapalli",
        "created" to "2023-06-21T12:11:21.355792",
        "createdBy" to "RAVI(prepare-a-case-for-court-1)",
        "lastUpdated" to "2023-06-21T12:11:21.355792",
        "lastUpdatedBy" to "RAVI(prepare-a-case-for-court-1)",
        "caseNumber" to "2106223516243653402",
      ),
      mapOf(
        "comment" to "Defendant details\\r\\nName\\tJohn Marston\\r\\nGender\\tMale\\r\\nDate of birth\\t28 February 1997 (25 years old)\\r\\nPhone number\\tUnavailable\\r\\nAddress\\t14 Tottenham Court Road\\r\\nLondon Road\\r\\nEngland\\r\\nUK\\r\\nEarth\\r\\nW1T 7RJ\\r\\nComments\\r\\nAdd notes and observations about this case. Your colleagues who use Prepare a Case will be able to read them.\\r\\n\\r\\nThese comments will not be saved to NDelius.\\r\\n\\r\\n",
        "author" to "Ravishankar Challapalli",
        "created" to "2023-06-21T12:11:21.355792",
        "createdBy" to "RAVI(prepare-a-case-for-court-1)",
        "lastUpdated" to "2023-06-21T12:11:21.355792",
        "lastUpdatedBy" to "RAVI(prepare-a-case-for-court-1)",
        "caseNumber" to "2106223516243653402",
      ),
    ),
    "hearingOutcomes" to arrayListOf(
      mapOf(
        "outcomeType" to "OTHER",
        "outcomeDate" to "2023-06-22T14:12:31.396105",
        "resultedDate" to "2023-09-12T15:30:13.558769",
        "state" to "RESULTED",
        "assignedTo" to "Ryan",
        "createdDate" to "2023-06-22T14:12:31.428778",
      ),
      mapOf(
        "outcomeType" to "ADJOURNED",
        "outcomeDate" to "2023-06-22T14:12:31.396105",
        "resultedDate" to "2023-09-12T15:30:13.558769",
        "state" to "RESULTED",
        "assignedTo" to "Johny Farrar",
        "createdDate" to "2023-06-22T14:12:31.428778",
      ),
    ),
    "hearingNotes" to arrayListOf(
      mapOf(
        "hearingId" to "605e08b9-8544-417e-84fa-39ce337ab04e",
        "note" to "This is a note",
        "author" to "Joana Aguia",
      ),
      mapOf(
        "hearingId" to "605e08b9-8544-417e-84fa-39ce337ab04e",
        "note" to "This is a note",
        "author" to "Joana Aguia",
      ),
    ),
  )
}
