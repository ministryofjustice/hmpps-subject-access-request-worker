package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfEducationEmploymentServiceTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders for Education and Employment Service`() {
    val serviceList = listOf(
      DpsService(
        name = "hmpps-education-employment-api",
        content = educationEmploymentServiceData,
      ),
    )
    generateSubjectAccessRequestPdf("dummy-template-hmpps-education-employment-api.pdf", serviceList)

    getGeneratedPdfDocument("dummy-template-hmpps-education-employment-api.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(2))
      assertThat(text).contains("Work Readiness")
    }
  }

  private val educationEmploymentServiceData: Any = mapOf(
    "offenderId" to "G1481GR",
    "createdDateTime" to arrayListOf(
      2023,
      3,
      24,
      13,
      59,
      16,
    ),
    "modifiedDateTime" to arrayListOf(
      2023,
      3,
      25,
      18,
      14,
      28,
    ),
    "profileData" to mapOf(
      "status" to "SUPPORT_NEEDED",
      "statusChange" to false,
      "statusChangeDate" to arrayListOf(
        2023,
        5,
        8,
        13,
        59,
        16,
      ),
      "prisonName" to "Moorland",
      "statusChangeType" to "NEW",
      "supportDeclined_history" to arrayListOf(
        mapOf(
          "modifiedDateTime" to arrayListOf(
            2023,
            11,
            15,
            18,
            14,
            4,
            936899979,
          ),
          "supportToWorkDeclinedReason" to arrayListOf(
            "FULL_TIME_CARER",
            "HEALTH",
          ),
          "supportToWorkDeclinedReasonOther" to "other reason",
          "circumstanceChangesRequiredToWork" to arrayListOf(
            "DEPENDENCY_SUPPORT",
            "MENTAL_HEALTH_SUPPORT",
          ),
          "circumstanceChangesRequiredToWorkOther" to "other changes",
        ),
      ),
      "supportAccepted_history" to arrayListOf(
        mapOf(
          "modifiedDateTime" to arrayListOf(
            2023,
            3,
            25,
            18,
            14,
            4,
            936899979,
          ),
          "actionsRequired" to mapOf(
            "modifiedDateTime" to arrayListOf(
              2023,
              3,
              25,
              18,
              14,
              4,
              832000000,
            ),
            "actions" to arrayListOf(
              mapOf(
                "todoItem" to "BANK_ACCOUNT",
                "status" to "COMPLETED",
                "other" to null,
                "id" to null,
              ),
            ),
          ),
          "workImpacts" to mapOf(
            "modifiedDateTime" to arrayListOf(
              2023,
              3,
              24,
              13,
              59,
              59,
              844000000,
            ),
            "abilityToWorkImpactedBy" to arrayListOf(
              "FAMILY_ISSUES",
              "PHYSICAL_HEALTH_ISSUES",
            ),
            "caringResponsibilitiesFullTime" to false,
            "ableToManageMentalHealth" to false,
            "ableToManageDependencies" to false,
          ),
          "workInterests" to mapOf(
            "modifiedDateTime" to arrayListOf(
              2023,
              3,
              24,
              14,
              0,
              29,
              221000000,
            ),
            "workTypesOfInterest" to arrayListOf(
              "OUTDOOR",
              "BEAUTY",
              "RETAIL",
            ),
            "workTypesOfInterestOther" to "",
            "jobOfParticularInterest" to "",
          ),
          "workExperience" to mapOf(
            "modifiedDateTime" to arrayListOf(
              2023,
              3,
              24,
              13,
              59,
              16,
              121000000,
            ),
            "previousWorkOrVolunteering" to "",
            "qualificationsAndTraining" to arrayListOf(
              "FIRST_AID",
              "FOOD_HYGIENE",
              "DRIVING_LICENSE",
            ),
            "qualificationsAndTrainingOther" to "",
          ),
        ),
      ),
      "supportDeclined" to mapOf(
        "modifiedDateTime" to arrayListOf(
          2023,
          11,
          15,
          18,
          14,
          4,
          936899979,
        ),
        "supportToWorkDeclinedReason" to arrayListOf(
          "FULL_TIME_CARER",
          "HEALTH",
        ),
        "supportToWorkDeclinedReasonOther" to "other reason",
        "circumstanceChangesRequiredToWork" to arrayListOf(
          "DEPENDENCY_SUPPORT",
          "MENTAL_HEALTH_SUPPORT",
        ),
        "circumstanceChangesRequiredToWorkOther" to "other changes",
      ),
      "supportAccepted" to mapOf(
        "modifiedDateTime" to arrayListOf(
          2023,
          3,
          25,
          18,
          14,
          28,
          677543359,
        ),
        "actionsRequired" to mapOf(
          "modifiedDateTime" to arrayListOf(
            2023,
            3,
            25,
            18,
            14,
            4,
            832000000,
          ),
          "actions" to arrayListOf(
            mapOf(
              "todoItem" to "BANK_ACCOUNT",
              "status" to "COMPLETED",
              "other" to null,
              "id" to arrayListOf(
                "BIRTH_CERTIFICATE",
                "PASSPORT",
              ),
            ),
          ),
        ),
        "workImpacts" to mapOf(
          "modifiedDateTime" to arrayListOf(
            2023,
            3,
            25,
            18,
            14,
            28,
            571000000,
          ),
          "abilityToWorkImpactedBy" to arrayListOf(
            "FAMILY_ISSUES",
            "CARING_RESPONSIBILITIES",
            "PHYSICAL_HEALTH_ISSUES",
          ),
          "caringResponsibilitiesFullTime" to false,
          "ableToManageMentalHealth" to false,
          "ableToManageDependencies" to true,
        ),
        "workInterests" to mapOf(
          "modifiedDateTime" to arrayListOf(
            2023,
            3,
            24,
            14,
            0,
            29,
            221000000,
          ),
          "workTypesOfInterest" to arrayListOf(
            "OUTDOOR",
            "BEAUTY",
            "RETAIL",
          ),
          "workTypesOfInterestOther" to "",
          "jobOfParticularInterest" to "",
        ),
        "workExperience" to mapOf(
          "modifiedDateTime" to arrayListOf(
            2023,
            3,
            24,
            13,
            59,
            16,
            121000000,
          ),
          "previousWorkOrVolunteering" to "",
          "qualificationsAndTraining" to arrayListOf(
            "FIRST_AID",
            "FOOD_HYGIENE",
            "DRIVING_LICENSE",
          ),
          "qualificationsAndTrainingOther" to "",
        ),
      ),
    ),
  )
}
