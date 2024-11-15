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
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfUseOfForceServiceTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Use of Force Service`() {
    whenever(userDetailsRepository.findByUsername("USERAL_ADM")).thenReturn(UserDetail("USERAL_ADM", "Reacher"))
    whenever(userDetailsRepository.findByUsername("USERAZ_ADM")).thenReturn(UserDetail("USERAZ_ADM", "Dixon"))
    whenever(userDetailsRepository.findByUsername("AND_USER")).thenReturn(UserDetail("AND_USER", "O'Donnell"))
    val serviceList = listOf(DpsService(name = "hmpps-uof-data-api", content = useOfForceServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-uof-template.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()
    val reader = PdfDocument(PdfReader("dummy-uof-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("Use of force")
    assertThat(text).contains("Dixon")
    assertThat(text).contains("O'Donnell")

    verify(userDetailsRepository, times(0)).findByUsername("USERAL_ADM")
    verify(userDetailsRepository, times(1)).findByUsername("USERAZ_ADM")
    verify(userDetailsRepository, times(1)).findByUsername("AND_USER")
    verifyNoMoreInteractions(prisonDetailsRepository)
  }

  private val useOfForceServiceData: ArrayList<Any> = arrayListOf(
    mapOf(
      "id" to 190,
      "sequenceNo" to 2,
      "createdDate" to "2020-09-04T12:12:53.812536",
      "updatedDate" to "2021-03-30T11:31:16.854361",
      "incidentDate" to "2020-09-07T02:02:00",
      "submittedDate" to "2021-03-30T11:31:16.853",
      "deleted" to "2021-11-30T15:47:13.139",
      "status" to "SUBMITTED",
      "agencyId" to "MDI",
      "userId" to "USERAL_ADM",
      "reporterName" to "Andrew Reacher",
      "offenderNo" to "A1234AA",
      "bookingId" to 1048991,
      "formResponse" to mapOf(
        "evidence" to mapOf(
          "cctvRecording" to "YES",
          "baggedEvidence" to true,
          "bodyWornCamera" to "YES",
          "photographsTaken" to false,
          "evidenceTagAndDescription" to arrayListOf(
            mapOf(
              "description" to "sasasasas",
              "evidenceTagReference" to "sasa",
            ),
            mapOf(
              "description" to "sasasasas 2",
              "evidenceTagReference" to "sasa 2",
            ),
          ),
          "bodyWornCameraNumbers" to arrayListOf(
            mapOf(
              "cameraNum" to "sdsds",
            ),
            mapOf(
              "cameraNum" to "xxxxx",
            ),
          ),
        ),
        "involvedStaff" to arrayListOf(
          mapOf(
            "name" to "Andrew User",
            "email" to "andrew.user@digital.justice.gov.uk",
            "staffId" to 486084,
            "username" to "USERAZ_ADM",
            "verified" to true,
            "activeCaseLoadId" to "MDI",
          ),
          mapOf(
            "name" to "Andrew User2",
            "email" to "andrew.user2@digital.justice.gov.uk",
            "staffId" to 486084,
            "username" to "AND_USER",
            "verified" to true,
            "activeCaseLoadId" to "MDI",
          ),
        ),
        "incidentDetails" to mapOf(
          "locationId" to 357591,
          "plannedUseOfForce" to false,
          "authorisedBy" to "",
          "witnesses" to arrayListOf(
            mapOf(
              "name" to "Andrew Bob",
            ),
            mapOf(
              "name" to "Andrew Jack",
            ),
          ),
        ),
        "useOfForceDetails" to mapOf(
          "bodyWornCamera" to "YES",
          "bodyWornCameraNumbers" to arrayListOf(
            mapOf(
              "cameraNum" to "sdsds",
            ),
            mapOf(
              "cameraNum" to "sdsds 2",
            ),
          ),
          "pavaDrawn" to false,
          "pavaDrawnAgainstPrisoner" to false,
          "pavaUsed" to false,
          "weaponsObserved" to "YES",
          "weaponTypes" to arrayListOf(
            mapOf(
              "weaponType" to "xxx",
            ),
            mapOf(
              "weaponType" to "yyy",
            ),
          ),
          "escortingHold" to false,
          "restraint" to true,
          "restraintPositions" to arrayListOf(
            "ON_BACK",
            "ON_FRONT",
          ),
          "batonDrawn" to false,
          "batonDrawnAgainstPrisoner" to false,
          "batonUsed" to false,
          "guidingHold" to false,
          "handcuffsApplied" to false,
          "positiveCommunication" to false,
          "painInducingTechniques" to false,
          "painInducingTechniquesUsed" to "NONE",
          "personalProtectionTechniques" to true,
        ),
        "reasonsForUseOfForce" to mapOf(
          "reasons" to arrayListOf(
            "FIGHT_BETWEEN_PRISONERS",
            "REFUSAL_TO_LOCATE_TO_CELL",
          ),
          "primaryReason" to "REFUSAL_TO_LOCATE_TO_CELL",
        ),
        "relocationAndInjuries" to mapOf(
          "relocationType" to "OTHER",
          "f213CompletedBy" to "adcdas",
          "prisonerInjuries" to false,
          "healthcareInvolved" to true,
          "healthcarePractionerName" to "dsffds",
          "prisonerRelocation" to "CELLULAR_VEHICLE",
          "relocationCompliancy" to false,
          "staffMedicalAttention" to true,
          "staffNeedingMedicalAttention" to arrayListOf(
            mapOf(
              "name" to "fdsfsdfs",
              "hospitalisation" to false,
            ),
            mapOf(
              "name" to "fdsfsdfs",
              "hospitalisation" to false,
            ),
          ),
          "prisonerHospitalisation" to false,
          "userSpecifiedRelocationType" to "fsf FGSDgf s gfsdgGG  gf ggrf",
        ),
      ),
      "statements" to arrayListOf(
        mapOf(
          "id" to 334,
          "reportId" to 280,
          "createdDate" to "2021-04-08T09:23:51.165439",
          "updatedDate" to "2021-04-21T10:09:25.626246",
          "submittedDate" to "2021-04-21T10:09:25.626246",
          "deleted" to "2021-04-21T10:09:25.626246",
          "nextReminderDate" to "2021-04-09T09:23:51.165",
          "overdueDate" to "2021-04-11T09:23:51.165",
          "removalRequestedDate" to "2021-04-21T10:09:25.626246",
          "userId" to "USERAZ_ADM",
          "name" to "Andrew Userz",
          "email" to "andrew.userz@digital.justice.gov.uk",
          "statementStatus" to "REMOVAL_REQUESTED",
          "lastTrainingMonth" to 1,
          "lastTrainingYear" to 2019,
          "jobStartYear" to 2019,
          "staffId" to 486084,
          "inProgress" to true,
          "removalRequestedReason" to "example",
          "statement" to "example",
          "statementAmendments" to arrayListOf(
            mapOf(
              "id" to 334,
              "statementId" to 198,
              "additionalComment" to "this is an additional comment",
              "dateSubmitted" to "2020-10-01T13:08:37.25919",
              "deleted" to "2022-10-01T13:08:37.25919",
            ),
            mapOf(
              "id" to 335,
              "statementId" to 199,
              "additionalComment" to "this is an additional additional comment",
              "dateSubmitted" to "2020-10-01T13:08:37.25919",
              "deleted" to "2022-10-01T13:08:37.25919",
            ),
          ),
        ),
        mapOf(
          "id" to 334,
          "reportId" to 280,
          "createdDate" to "2021-04-08T09:23:51.165439",
          "updatedDate" to "2021-04-21T10:09:25.626246",
          "submittedDate" to "2021-04-21T10:09:25.626246",
          "deleted" to "2021-04-21T10:09:25.626246",
          "nextReminderDate" to "2021-04-09T09:23:51.165",
          "overdueDate" to "2021-04-11T09:23:51.165",
          "removalRequestedDate" to "2021-04-21T10:09:25.626246",
          "userId" to "USERAZ_ADM",
          "name" to "Andrew Userz",
          "email" to "andrew.userz@digital.justice.gov.uk",
          "statementStatus" to "REMOVAL_REQUESTED",
          "lastTrainingMonth" to 1,
          "lastTrainingYear" to 2019,
          "jobStartYear" to 2019,
          "staffId" to 486084,
          "inProgress" to true,
          "removalRequestedReason" to "example",
          "statement" to "example",
          "statementAmendments" to arrayListOf(
            mapOf(
              "id" to 334,
              "statementId" to 198,
              "additionalComment" to "this is an additional comment",
              "dateSubmitted" to "2020-10-01T13:08:37.25919",
              "deleted" to "2022-10-01T13:08:37.25919",
            ),
            mapOf(
              "id" to 335,
              "statementId" to 199,
              "additionalComment" to "this is an additional additional comment",
              "dateSubmitted" to "2020-10-01T13:08:37.25919",
              "deleted" to "2022-10-01T13:08:37.25919",
            ),
          ),
        ),
      ),
    ),
  )
}
