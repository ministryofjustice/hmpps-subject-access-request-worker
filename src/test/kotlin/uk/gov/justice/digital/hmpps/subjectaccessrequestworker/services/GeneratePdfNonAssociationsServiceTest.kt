package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfNonAssociationsServiceTest : BaseGeneratePdfTest() {

  @Test
  fun `generatePdfService renders for Non-associations Service`() {
    val serviceList = listOf(DpsService(name = "hmpps-non-associations-api", content = nonAssociationsServiceData))
    generateSubjectAccessRequestPdf("dummy-template-non-associations.pdf", serviceList)

    getGeneratedPdfDocument("dummy-template-non-associations.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(2))
      assertThat(text).contains("Non-associations")
    }
  }

  private val nonAssociationsServiceData = mapOf(
    "prisonerNumber" to "A4743DZ",
    "firstName" to "SOLOMON",
    "lastName" to "ANTHONY",
    "prisonId" to "LEI",
    "prisonName" to "Leeds (HMP)",
    "cellLocation" to "RECP",
    "openCount" to 1,
    "closedCount" to 0,
    "nonAssociations" to arrayListOf(
      mapOf(
        "id" to 83493,
        "role" to "PERPETRATOR",
        "roleDescription" to "Perpetrator",
        "reason" to "ORGANISED_CRIME",
        "reasonDescription" to "Organised crime",
        "restrictionType" to "LANDING",
        "restrictionTypeDescription" to "Cell and landing",
        "comment" to "This is a test for SAR",
        "authorisedBy" to "MUSER_GEN",
        "whenCreated" to "2024-05-07T14:49:51",
        "whenUpdated" to "2024-05-07T14:49:51",
        "updatedBy" to "MUSER_GEN",
        "isClosed" to false,
        "closedBy" to null,
        "closedReason" to null,
        "closedAt" to null,
        "otherPrisonerDetails" to mapOf(
          "prisonerNumber" to "G4769GD",
          "role" to "PERPETRATOR",
          "roleDescription" to "Perpetrator",
          "firstName" to "UDFSANAYE",
          "lastName" to "AARELL",
          "prisonId" to "PRI",
          "prisonName" to "Parc (HMP)",
          "cellLocation" to "T-5-41",
        ),
      ),
      mapOf(
        "id" to 83493,
        "role" to "PERPETRATOR",
        "roleDescription" to "Perpetrator",
        "reason" to "ORGANISED_CRIME",
        "reasonDescription" to "Organised crime",
        "restrictionType" to "LANDING",
        "restrictionTypeDescription" to "Cell and landing",
        "comment" to "This is a test for SAR",
        "authorisedBy" to "MUSER_GEN",
        "whenCreated" to "2024-05-07T14:49:51",
        "whenUpdated" to "2024-05-07T14:49:51",
        "updatedBy" to "MUSER_GEN",
        "isClosed" to false,
        "closedBy" to null,
        "closedReason" to null,
        "closedAt" to null,
        "otherPrisonerDetails" to mapOf(
          "prisonerNumber" to "G4769GD",
          "role" to "PERPETRATOR",
          "roleDescription" to "Perpetrator",
          "firstName" to "UDFSANAYE",
          "lastName" to "AARELL",
          "prisonId" to "PRI",
          "prisonName" to "Parc (HMP)",
          "cellLocation" to "T-5-41",
        ),
      ),
    ),
  )
}
