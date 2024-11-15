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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfHomDetentionCurfewServiceTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Home Detention Curfew Service`() {
    val serviceList = listOf(DpsService(name = "hmpps-hdc-api", content = homeDetentionCurfewServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-hdc-template.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()
    val reader = PdfDocument(PdfReader("dummy-hdc-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))
    assertThat(text).contains("Home Detention Curfew")
  }

  private val homeDetentionCurfewServiceData: Map<Any, Any> = mapOf(
    "licences" to arrayListOf(
      mapOf(
        "id" to 1626,
        "prisonNumber" to "G1556UH",
        "bookingId" to 1108337,
        "stage" to "PROCESSING_RO",
        "version" to 1,
        "transitionDate" to "2024-03-18T09:24:35.473079",
        "varyVersion" to 0,
        "additionalConditionsVersion" to null,
        "standardConditionsVersion" to null,
        "deletedAt" to "2024-03-18T09:25:06.780003",
        "licence" to mapOf(
          "eligibility" to mapOf(
            "crdTime" to mapOf(
              "decision" to "No",
            ),
            "excluded" to mapOf(
              "decision" to "No",
            ),
            "suitability" to mapOf(
              "decision" to "No",
            ),
          ),
          "bassReferral" to mapOf(
            "bassRequest" to mapOf(
              "specificArea" to "No",
              "bassRequested" to "Yes",
              "additionalInformation" to "",
            ),
          ),
          "proposedAddress" to mapOf(
            "optOut" to mapOf(
              "decision" to "No",
            ),
          ),
          "addressProposed" to mapOf(
            "decision" to "No",
          ),
        ),
      ),
    ),
    "licenceVersions" to arrayListOf(
      mapOf(
        "id" to 446,
        "prisonNumber" to "G1556UH",
        "bookingId" to 1108337,
        "timestamp" to "2024-03-15T10:48:50.888663",
        "version" to 1,
        "template" to "hdc_ap",
        "varyVersion" to 0,
        "deletedAt" to "2024-03-15T11:11:14.361319",
        "licence" to mapOf(
          "risk" to mapOf(
            "riskManagement" to mapOf(
              "version" to "3",
              "emsInformation" to "No",
              "pomConsultation" to "Yes",
              "mentalHealthPlan" to "No",
              "unsuitableReason" to "",
              "hasConsideredChecks" to "Yes",
              "manageInTheCommunity" to "Yes",
              "emsInformationDetails" to "",
              "riskManagementDetails" to "",
              "proposedAddressSuitable" to "Yes",
              "awaitingOtherInformation" to "No",
              "nonDisclosableInformation" to "No",
              "nonDisclosableInformationDetails" to "",
              "manageInTheCommunityNotPossibleReason" to "",
            ),
          ),
          "curfew" to mapOf(
            "firstNight" to mapOf(
              "firstNightFrom" to "15:00",
              "firstNightUntil" to "07:00",
            ),
          ),
          "curfewHours" to mapOf(
            "allFrom" to "19:00",
            "allUntil" to "07:00",
            "fridayFrom" to "19:00",
            "mondayFrom" to "19:00",
            "sundayFrom" to "19:00",
            "fridayUntil" to "07:00",
            "mondayUntil" to "07:00",
            "sundayUntil" to "07:00",
            "tuesdayFrom" to "19:00",
            "saturdayFrom" to "19:00",
            "thursdayFrom" to "19:00",
            "tuesdayUntil" to "07:00",
            "saturdayUntil" to "07:00",
            "thursdayUntil" to "07:00",
            "wednesdayFrom" to "19:00",
            "wednesdayUntil" to "07:00",
            "daySpecificInputs" to "No",
          ),
          "victim" to mapOf(
            "victimLiaison" to mapOf(
              "decision" to "No",
            ),
          ),
          "approval" to mapOf(
            "release" to mapOf(
              "decision" to "Yes",
              "decisionMaker" to "Ann User",
              "reasonForDecision" to "",
            ),
          ),
          "consideration" to mapOf(
            "decision" to "Yes",
          ),
          "document" to mapOf(
            "template" to mapOf(
              "decision" to "hdc_ap",
              "offenceCommittedBeforeFeb2015" to "No",
            ),
          ),
          "reporting" to mapOf(
            "reportingInstructions" to mapOf(
              "name" to "sam",
              "postcode" to "S3 8RD",
              "telephone" to "47450",
              "townOrCity" to "Sheffield",
              "organisation" to "crc",
              "reportingDate" to "12/12/2024",
              "reportingTime" to "12:12",
              "buildingAndStreet1" to "10",
              "buildingAndStreet2" to "street",
            ),
          ),
          "eligibility" to mapOf(
            "crdTime" to mapOf(
              "decision" to "No",
            ),
            "excluded" to mapOf(
              "decision" to "No",
            ),
            "suitability" to mapOf(
              "decision" to "No",
            ),
          ),
          "finalChecks" to mapOf(
            "onRemand" to mapOf(
              "decision" to "No",
            ),
            "segregation" to mapOf(
              "decision" to "No",
            ),
            "seriousOffence" to mapOf(
              "decision" to "No",
            ),
            "confiscationOrder" to mapOf(
              "decision" to "No",
            ),
            "undulyLenientSentence" to mapOf(
              "decision" to "No",
            ),
          ),
          "bassReferral" to mapOf(
            "bassOffer" to mapOf(
              "bassArea" to "Reading",
              "postCode" to "RG1 6HM",
              "telephone" to "",
              "addressTown" to "Reading",
              "addressLine1" to "The Street",
              "addressLine2" to "",
              "bassAccepted" to "Yes",
              "bassOfferDetails" to "",
            ),
            "bassRequest" to mapOf(
              "specificArea" to "No",
              "bassRequested" to "Yes",
              "additionalInformation" to "",
            ),
            "bassAreaCheck" to mapOf(
              "bassAreaReason" to "",
              "bassAreaCheckSeen" to "true",
              "approvedPremisesRequiredYesNo" to "No",
            ),
          ),
          "proposedAddress" to mapOf(
            "optOut" to mapOf(
              "decision" to "No",
            ),
            "addressProposed" to mapOf(
              "decision" to "No",
            ),
          ),
          "licenceConditions" to mapOf(
            "standard" to mapOf(
              "additionalConditionsRequired" to "No",
            ),
          ),
        ),
      ),
    ),
    "auditEvents" to arrayListOf(
      mapOf(
        "id" to 40060,
        "timestamp" to "2024-08-23T09:36:51.186289",
        "user" to "cpxUKKZdbW",
        "action" to "UPDATE_SECTION",
        "details" to mapOf(
          "path" to "/hdc/curfew/approvedPremises/1108337",
          "bookingId" to "1108337",
          "userInput" to mapOf(
            "required" to "Yes",
          ),
        ),
      ),
    ),
  )
}
