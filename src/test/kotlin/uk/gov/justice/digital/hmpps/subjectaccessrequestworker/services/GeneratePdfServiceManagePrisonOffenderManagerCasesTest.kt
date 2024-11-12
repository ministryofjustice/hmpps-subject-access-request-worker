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

class GeneratePdfServiceManagePrisonOffenderManagerCasesTest {

  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  private fun writeAndThenReadPdf(
    testInput: Map<String, Any?>?,
  ): PdfDocument {
    val testFileName = "dummy-template-moic.pdf"
    val testResponseObject = listOf(DpsService(name = "offender-management-allocation-manager", content = testInput))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream(testFileName)))
    Document(pdfDocument).use {
      generatePdfService.addData(pdfDocument, it, testResponseObject)
    }
    return PdfDocument(PdfReader(testFileName))
  }

  @Test
  fun `generatePdfService renders for Manage Prison Offender Manager Cases API with no data held`() {
    writeAndThenReadPdf(null).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Manage Prison Offender Manager Cases")
      assertThat(text).doesNotContain("NOMS number")
      assertThat(text).contains("No data held")
    }
  }

  @Test
  fun `generatePdfService renders for Manage Prison Offender Manager Cases API with data missing`() {
    val testInput = mapOf(
      "nomsNumber" to "G9979UV",
      "allocationHistory" to emptyArray<Map<String, Any>>(),
      "auditEvents" to mapOf(
        "createdAt" to "2023-07-26T12:59:57.966+01:00",
      ),
      "calculatedEarlyAllocationStatus" to mapOf(
        "eligible" to false,
        "createdAt" to "2021-07-29T13:49:17.556+01:00",
        "updatedAt" to "2021-07-29T13:49:17.556+01:00",
      ),
      "calculatedHandoverDate" to mapOf(
        "startDate" to null,
        "handoverDate" to null,
        "reason" to "pre_omic_rules",
        "createdAt" to "2023-07-14T07:34:20.849+01:00",
        "updatedAt" to "2023-07-14T07:34:20.849+01:00",
        "responsibility" to "Community",
        "lastCalculatedAt" to null,
      ),
      "caseInformation" to mapOf(
        "tier" to "A",
        "crn" to null,
        "mappaLevel" to null,
        "manualEntry" to true,
        "createdAt" to "2023-07-13T18:02:56.998+01:00",
        "updatedAt" to "2023-09-05T16:15:37.677+01:00",
        "probationService" to "Wales",
        "comName" to "Fake COM 1",
        "teamName" to "Fake Team 1",
        "localDeliveryUnitId" to 140,
        "lduCode" to "N57KTLD",
        "comEmail" to "fake-com-1@example.org",
        "activeVlo" to false,
        "enhancedResourcing" to true,
      ),
      "earlyAllocations" to emptyArray<Map<String, Any>>(),
      "emailHistories" to emptyArray<Map<String, Any>>(),
      "handoverProgressChecklist" to mapOf(
        "reviewedOasys" to true,
        "contactedCom" to true,
        "attendedHandoverMeeting" to true,
        "sentHandoverReport" to true,
        "createdAt" to "2023-07-13T18:02:56.998+01:00",
        "updatedAt" to "2023-07-13T18:02:56.998+01:00",
      ),
      "offenderEmailSent" to emptyArray<Map<String, Any>>(),
      "paroleRecord" to mapOf(
        "paroleReviewDate" to "2023-07-13T18:02:56.998+01:00",
        "createdAt" to "2023-07-13T18:02:56.998+01:00",
        "updatedAt" to "2023-07-13T18:02:56.998+01:00",
      ),
      "responsibility" to mapOf(
        "reason" to "this is an example reason",
        "reasonText" to "this is an example reason",
        "value" to "some value",
        "createdAt" to "2023-07-13T18:02:56.998+01:00",
        "updatedAt" to "2023-07-13T18:02:56.998+01:00",
      ),
      "victimLiaisonOfficers" to emptyArray<Map<String, Any>>(),
    )
    writeAndThenReadPdf(testInput).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Manage Prison Offender Manager Cases")
      assertThat(text).contains("NOMS number G9979UV")
    }
  }

  @Test
  fun `generatePdfService renders for Manage Prison Offender Manager Cases API`() {
    val testInput = mapOf(
      "nomsNumber" to "G9979UV",
      "allocationHistory" to arrayListOf(
        mapOf(
          "prison" to "LEI",
          "allocatedAtTier" to "A",
          "overrideReasons" to "this is an example reason",
          "overrideDetail" to "this is an example detail",
          "message" to "test",
          "suitabilityDetail" to "this is an example detail",
          "primaryPomName" to "GREEN, MATT",
          "secondaryPomName" to null,
          "createdByName" to "Stephanie Batliner",
          "primaryPomNomisId" to 485807,
          "secondaryPomNomisId" to null,
          "event" to "allocate_primary_pom",
          "eventTrigger" to "user",
          "createdAt" to "2023-07-26T12:59:57.966+01:00",
          "updatedAt" to "2024-04-09T10:56:47.696+01:00",
          "primaryPomAllocatedAt" to "2023-07-26T12:59:57.961+01:00",
          "recommendedPomType" to "prison",
        ),
      ),
      "auditEvents" to mapOf(
        "createdAt" to "2023-07-26T12:59:57.966+01:00",
      ),
      "calculatedEarlyAllocationStatus" to mapOf(
        "eligible" to false,
        "createdAt" to "2021-07-29T13:49:17.556+01:00",
        "updatedAt" to "2021-07-29T13:49:17.556+01:00",
      ),
      "calculatedHandoverDate" to mapOf(
        "startDate" to null,
        "handoverDate" to null,
        "reason" to "pre_omic_rules",
        "createdAt" to "2023-07-14T07:34:20.849+01:00",
        "updatedAt" to "2023-07-14T07:34:20.849+01:00",
        "responsibility" to "Community",
        "lastCalculatedAt" to null,
      ),
      "caseInformation" to mapOf(
        "tier" to "A",
        "crn" to null,
        "mappaLevel" to null,
        "manualEntry" to true,
        "createdAt" to "2023-07-13T18:02:56.998+01:00",
        "updatedAt" to "2023-09-05T16:15:37.677+01:00",
        "probationService" to "Wales",
        "comName" to "Fake COM 1",
        "teamName" to "Fake Team 1",
        "localDeliveryUnitId" to 140,
        "lduCode" to "N57KTLD",
        "comEmail" to "fake-com-1@example.org",
        "activeVlo" to false,
        "enhancedResourcing" to true,
      ),
      "earlyAllocations" to arrayListOf(
        mapOf(
          "oasysRiskAssessmentDate" to "2023-07-13T18:02:56.998+01:00",
          "convictedUnderTerrorisomAct2000" to true,
          "highProfile" to true,
          "seriousCrimePreventionOrder" to true,
          "mappaLevel3" to true,
          "cppcCase" to true,
          "highRiskOfSeriousHarm" to true,
          "mappaLevel2" to true,
          "pathfinderProcess" to true,
          "otherReason" to true,
          "extremismSeparation" to true,
          "dueForReleaseInLessThan24months" to true,
          "approved" to true,
          "reason" to "This is an example reason",
          "communityDecision" to true,
          "prison" to "LEI",
          "createdByFirstname" to "James",
          "createdByLastname" to "Blogg",
          "updatedByFirstname" to "James",
          "updatedByLastname" to "Jones",
          "createdWithinReferralWindow" to true,
          "outcome" to "success",
          "createdAt" to "2023-07-13T18:02:56.998+01:00",
          "updatedAt" to "2023-07-13T18:02:56.998+01:00",
        ),
      ),
      "emailHistories" to arrayListOf(
        mapOf(
          "prison" to "LEI",
          "name" to "James",
          "email" to "james@gmail.com",
          "event" to "some event",
          "createdAt" to "2023-07-13T18:02:56.998+01:00",
          "updatedAt" to "2023-07-13T18:02:56.998+01:00",
        ),
        mapOf(
          "prison" to "LEI",
          "name" to "James2",
          "email" to "james@gmail.com",
          "event" to "some other event",
          "createdAt" to "2023-07-13T18:02:56.998+01:00",
          "updatedAt" to "2023-07-13T18:02:56.998+01:00",
        ),
      ),
      "handoverProgressChecklist" to mapOf(
        "reviewedOasys" to true,
        "contactedCom" to true,
        "attendedHandoverMeeting" to true,
        "sentHandoverReport" to true,
        "createdAt" to "2023-07-13T18:02:56.998+01:00",
        "updatedAt" to "2023-07-13T18:02:56.998+01:00",
      ),
      "offenderEmailSent" to arrayListOf(
        mapOf(
          "staffMemberId" to "TESTID",
          "offenderEmailType" to "some email type",
          "createdAt" to "2023-07-13T18:02:56.998+01:00",
          "updatedAt" to "2023-07-13T18:02:56.998+01:00",
        ),
      ),
      "paroleRecord" to mapOf(
        "paroleReviewDate" to "2023-07-13T18:02:56.998+01:00",
        "createdAt" to "2023-07-13T18:02:56.998+01:00",
        "updatedAt" to "2023-07-13T18:02:56.998+01:00",
      ),
      "responsibility" to mapOf(
        "reason" to "this is an example reason",
        "reasonText" to "this is an example reason",
        "value" to "some value",
        "createdAt" to "2023-07-13T18:02:56.998+01:00",
        "updatedAt" to "2023-07-13T18:02:56.998+01:00",
      ),
      "victimLiaisonOfficers" to arrayListOf(
        mapOf(
          "firstName" to "James",
          "lastName" to "Johnson",
          "email" to "jj@gmail.com",
          "createdAt" to "2023-07-13T18:02:56.998+01:00",
          "updatedAt" to "2023-07-13T18:02:56.998+01:00",
        ),
        mapOf(
          "firstName" to "James",
          "lastName" to "Johnson-Barnes",
          "email" to "jj@gmail.com",
          "createdAt" to "2023-07-13T18:02:56.998+01:00",
          "updatedAt" to "2023-07-13T18:02:56.998+01:00",
        ),
      ),
    )
    writeAndThenReadPdf(testInput).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Manage Prison Offender Manager Cases")
      assertThat(text).contains("NOMS number G9979UV")
      assertThat(text).contains("Message")
      val thirdPageText = PdfTextExtractor.getTextFromPage(it.getPage(3))
      assertThat(thirdPageText).contains("Local delivery unit ID")
      assertThat(thirdPageText).contains("High risk of serious harm")
      val fourthPageText = PdfTextExtractor.getTextFromPage(it.getPage(4))
      assertThat(fourthPageText).contains("some other event")
      val fifthPageText = PdfTextExtractor.getTextFromPage(it.getPage(5))
      assertThat(fifthPageText).contains("Johnson-Barnes")
    }
  }
}
