package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfActivitiesServiceTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders a template given an activities template`() {
    val serviceList = listOf(DpsService(name = "hmpps-activities-management-api", content = activitiesServiceData))
    generateSubjectAccessRequestPdf("dummy-activities-template.pdf", serviceList)

    getGeneratedPdfDocument("dummy-activities-template.pdf").use { doc ->
      val text = PdfTextExtractor.getTextFromPage(doc.getPage(2))
      assertThat(text).contains("Activities")
    }
  }

  @Test
  fun `generatePdfService renders a template given an activities template with missing data`() {
    val serviceList = listOf(
      DpsService(
        name = "hmpps-activities-management-api",
        content = activitiesServicePartialData,
      ),
    )
    generateSubjectAccessRequestPdf("dummy-activities-template-incomplete.pdf", serviceList)

    getGeneratedPdfDocument("dummy-activities-template-incomplete.pdf").use { reader ->
      val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))
      assertThat(text).contains("Activities")
    }
  }

  private val activitiesServiceData: ArrayList<Any> = arrayListOf(
    mapOf(
      "prisonerNumber" to "A4743DZ",
      "fromDate" to "1970-01-01",
      "toDate" to "2000-01-01",
      "allocations" to arrayListOf(
        mapOf(
          "allocationId" to 16,
          "prisonCode" to "LEI",
          "prisonerStatus" to "ENDED",
          "startDate" to "2023-07-21",
          "endDate" to "2023-07-21",
          "activityId" to 3,
          "activitySummary" to "QAtestingKitchenActivity",
          "payBand" to "Pay band 5",
          "createdDate" to "2023-07-20",
        ),
        mapOf(
          "allocationId" to 10,
          "prisonCode" to "LEI",
          "prisonerStatus" to "NEW",
          "startDate" to "2023-07-21",
          "endDate" to "2023-07-21",
          "activityId" to 4,
          "activitySummary" to "QAtestingKitchenActivity",
          "payBand" to "Pay band 5",
          "createdDate" to "2023-07-20",
        ),
      ),
      "attendanceSummary" to arrayListOf(
        mapOf(
          "attendanceReasonCode" to "ATTENDED",
          "count" to 12,
        ),
        mapOf(
          "attendanceReasonCode" to "CANCELLED",
          "count" to 8,
        ),
      ),
      "waitingListApplications" to arrayListOf(
        mapOf(
          "waitingListId" to 1,
          "prisonCode" to "LEI",
          "activitySummary" to "Summary",
          "applicationDate" to "2023-08-11",
          "originator" to "Prison staff",
          "status" to "APPROVED",
          "statusDate" to "2022-11-12",
          "comments" to null,
          "createdDate" to "2023-08-10",
        ),
        mapOf(
          "waitingListId" to 10,
          "prisonCode" to "LEI",
          "activitySummary" to "Summary",
          "applicationDate" to "2024-08-11",
          "originator" to "Prison staff",
          "status" to "APPROVED",
          "statusDate" to null,
          "comments" to null,
          "createdDate" to "2023-08-10",
        ),
      ),
      "appointments" to arrayListOf(
        mapOf(
          "appointmentId" to 18305,
          "prisonCode" to "LEI",
          "categoryCode" to "CAREER",
          "startDate" to "2023-08-11",
          "startTime" to "10:00",
          "endTime" to "11:00",
          "extraInformation" to "",
          "attended" to "Unmarked",
          "createdDate" to "2023-08-10",
        ),
        mapOf(
          "appointmentId" to 16340,
          "prisonCode" to "LEI",
          "categoryCode" to "CAREER",
          "startDate" to "2023-08-11",
          "startTime" to "10:00",
          "endTime" to "11:00",
          "extraInformation" to "",
          "attended" to "Unmarked",
          "createdDate" to "2023-08-10",
        ),
      ),
    ),
  )

  val activitiesServicePartialData: ArrayList<Any> = arrayListOf(
    mapOf(
      "prisonerNumber" to "A4743DZ",
      "fromDate" to "1970-01-01",
      "toDate" to "2000-01-01",
      "attendanceSummary" to arrayListOf(
        mapOf(
          "attendanceReasonCode" to "ATTENDED",
          "count" to 12,
        ),
        mapOf(
          "attendanceReasonCode" to "CANCELLED",
          "count" to 8,
        ),
      ),
      "waitingListApplications" to arrayListOf(
        mapOf(
          "waitingListId" to 1,
          "prisonCode" to "LEI",
          "activitySummary" to "Summary",
          "applicationDate" to "2023-08-11",
          "originator" to "Prison staff",
          "status" to "APPROVED",
          "statusDate" to "2022-11-12",
          "comments" to null,
          "createdDate" to "2023-08-10",
        ),
        mapOf(
          "waitingListId" to 10,
          "prisonCode" to "LEI",
          "activitySummary" to "Summary",
          "applicationDate" to "2024-08-11",
          "originator" to "Prison staff",
          "status" to "APPROVED",
          "statusDate" to null,
          "comments" to null,
          "createdDate" to "2023-08-10",
        ),
      ),
      "appointments" to arrayListOf(
        mapOf(
          "appointmentId" to 18305,
          "prisonCode" to "LEI",
          "categoryCode" to "CAREER",
          "startDate" to "2023-08-11",
          "startTime" to "10:00",
          "endTime" to "11:00",
          "extraInformation" to "",
          "attended" to "Unmarked",
          "createdDate" to "2023-08-10",
        ),
        mapOf(
          "appointmentId" to 16340,
          "prisonCode" to "LEI",
          "categoryCode" to "CAREER",
          "startDate" to "2023-08-11",
          "startTime" to "10:00",
          "endTime" to "11:00",
          "extraInformation" to "",
          "attended" to "Unmarked",
          "createdDate" to "2023-08-10",
        ),
      ),
    ),
  )
}
