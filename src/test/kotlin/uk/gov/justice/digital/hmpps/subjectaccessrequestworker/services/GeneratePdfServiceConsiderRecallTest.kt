package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.UserDetail

class GeneratePdfServiceConsiderRecallTest : BaseGeneratePdfTest() {

  private fun writeAndThenReadPdf(
    testInput: Map<String, Any?>?,
  ): PdfDocument {
    whenever(userDetailsRepository.findByUsername("USERA_ADM"))
      .thenReturn(UserDetail("USERA_ADM", "Reacher"))

    val testFileName = "dummy-template-recall.pdf"
    val serviceList = listOf(DpsService(name = "make-recall-decision-api", content = testInput))
    generateSubjectAccessRequestPdf(testFileName, serviceList)

    return getGeneratedPdfDocument(testFileName)
  }

  @Test
  fun `generatePdfService renders for Consider a Recall API with no data held`() {
    writeAndThenReadPdf(null).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Consider a Recall")
      assertThat(text).doesNotContain("CRN")
      assertThat(text).contains("No data held")
      verifyNoInteractions(userDetailsRepository)
    }
  }

  @Test
  fun `generatePdfService renders for Consider a Recall API`() {
    writeAndThenReadPdf(testInput).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      val thirdPageText = PdfTextExtractor.getTextFromPage(it.getPage(3))
      val lastPageText = PdfTextExtractor.getTextFromPage(it.getPage(12))

      assertThat(text).contains("Consider a Recall")
      assertThat(text).contains("Sensitive")
      assertThat(text).contains("No")
      assertThat(text).contains("hacking the monitor")
      assertThat(text).contains("Reacher")

      assertThat(thirdPageText).contains("Local police contact")
      assertThat(thirdPageText).contains("If we transmit the interface")

      assertThat(lastPageText).contains("Booking momento")
      verify(userDetailsRepository, times(1)).findByUsername("USERA_ADM")
    }
  }

  private val testInput = mapOf(
    "recommendationId" to "1513981272",
    "crn" to "X098092",
    "recommendations" to arrayListOf(
      mapOf(
        "crn" to "X098092",
        "sensitive" to false,
        "ppudRecordPresent" to false,
        "recallConsideredList" to arrayListOf(
          mapOf(
            "id" to "139862450",
            "userId" to "USERA_ADM",
            "createdDate" to "2023-11-01",
            "userName" to "user",
            "recallConsideredDetail" to "some details",
          ),
        ),
        "recallType" to mapOf(
          "selected" to mapOf(
            "value" to "NO_RECALL",
            "details" to "details",
          ),
          "allOptions" to arrayListOf(
            mapOf(
              "value" to "STANDARD",
              "text" to "Standard recall",
            ),
            mapOf(
              "value" to "FIXED_TERM",
              "text" to "Fixed term recall",
            ),
            mapOf(
              "value" to "NO_RECALL",
              "text" to "No recall",
            ),
          ),
        ),
        "sendSpoRationaleToDelius" to true,
        "managerRecallDecision" to mapOf(
          "selected" to mapOf(
            "value" to "NO_RECALL",
            "details" to "hacking the monitor won't do anything, we need to reboot the digital TLS capacitor!",
          ),
          "allOptions" to arrayListOf(
            mapOf(
              "value" to "STANDARD",
              "text" to "Standard recall",
            ),
            mapOf(
              "value" to "FIXED_TERM",
              "text" to "Fixed term recall",
            ),
            mapOf(
              "value" to "NO_RECALL",
              "text" to "No recall",
            ),
          ),
          "isSentToDelius" to true,
          "createdBy" to "mrd spo",
          "createdDate" to "2023-09-01T09:40:29.810Z",
        ),
        "considerationRationale" to mapOf(
          "createdBy" to "mrd spo",
          "createdDate" to "2023-09-01",
          "createdTime" to "09:40:29.810",
          "sensitive" to false,
        ),
        "custodyStatus" to mapOf(
          "code" to "HA67",
          "description" to "some description",
        ),
        "localPoliceContact" to mapOf(
          "contactName" to "name",
          "phoneNumber" to "01234",
          "faxNumber" to "0456",
          "emailAddress" to "mail@gmail",
        ),
        "responseToProbation" to "If we transmit the interface, we can get to the PCI pixel through the primary CSS microchip!",
        "thoughtsLeadingToRecall" to "some text",
        "triggerLeadingToRecall" to "Use the mobile JBOD matrix, then you can index the open-source card!",
        "whatLedToRecall" to "some text",
        "isThisAnEmergencyRecall" to true,
        "isIndeterminateSentence" to false,
        "isExtendedSentence" to false,
        "activeCustodialConvictionCount" to 1,
        "hasVictimsInContactScheme" to mapOf(
          "selected" to "YES",
          "allOptions" to arrayListOf(
            mapOf(
              "value" to "STANDARD",
              "text" to "Standard recall",
            ),
            mapOf(
              "value" to "FIXED_TERM",
              "text" to "Fixed term recall",
            ),
            mapOf(
              "value" to "NO_RECALL",
              "text" to "No recall",
            ),
          ),
        ),
        "indeterminateSentenceType" to mapOf(
          "selected" to "NO",
          "allOptions" to arrayListOf(
            mapOf(
              "value" to "STANDARD",
              "text" to "Standard recall",
            ),
            mapOf(
              "value" to "FIXED_TERM",
              "text" to "Fixed term recall",
            ),
            mapOf(
              "value" to "NO_RECALL",
              "text" to "No recall",
            ),
          ),
        ),
        "dateVloInformed" to "2023-09-01T09:40:29.810Z",
        "hasArrestIssues" to mapOf(
          "selected" to true,
          "details" to "some details",
        ),
        "hasContrabandRisk" to mapOf(
          "selected" to true,
          "details" to "some details",
        ),
        "status" to "DRAFT",
        "region" to "London",
        "localDeliveryUnit" to "All London",
        "userNameDntrLetterCompletedBy" to "person",
        "lastDntrLetterADownloadDateTime" to "2023-09-01T09:40:29.810Z",
        "reviewPractitionersConcerns" to true,
        "odmName" to "person",
        "spoRecallType" to "NO_RECALL",
        "spoRecallRationale" to "hacking the monitor won't do anything, we need to reboot the digital TLS capacitor!",
        "spoDeleteRecommendationRationale" to "some text",
        "sendSpoDeleteRationaleToDelius" to false,
        "spoCancelRecommendationRationale" to "some text",
        "reviewOffenderProfile" to true,
        "explainTheDecision" to true,
        "lastModifiedBy" to "MARD_PO",
        "lastModifiedByUserName" to "Make Recall Decisions Probation Officer",
        "lastModifiedDate" to "2023-09-01T09:40:43.432Z",
        "createdBy" to "MARD_PO",
        "createdByUserFullName" to "NAME",
        "createdDate" to "2023-09-01T09:40:02.631Z",
        "personOnProbation" to mapOf(
          "name" to "Harry Smith",
          "firstName" to "Harry",
          "surname" to "Smith",
          "middleNames" to "Tom",
          "gender" to "Male",
          "ethnicity" to "",
          "dateOfBirth" to "1990-09-15",
          "croNumber" to "64941/08C",
          "mostRecentPrisonerNumber" to "",
          "nomsNumber" to "B8411AV",
          "pncNumber" to "2008/0545166T",
          "mappa" to mapOf(
            "level" to 1,
            "startDate" to "2023-09-01T09:40:29.810Z",
            "category" to 2,
          ),
          "addresses" to arrayListOf(
            mapOf(
              "line1" to "",
              "line2" to "",
              "town" to "",
              "postcode" to "",
              "noFixedAbode" to false,
            ),
          ),
          "primaryLanguage" to "",
          "hasBeenReviewed" to false,
        ),
        "convictionDetail" to mapOf(
          "indexOffenceDescription" to "some text",
          "dateOfOriginalOffence" to "2023-09-01T09:40:29.810Z",
          "dateOfSentence" to "2023-09-01T09:40:29.810Z",
          "lengthOfSentence" to 1,
          "lengthOfSentenceUnits" to "some text",
          "sentenceDescription" to "some text",
          "licenceExpiryDate" to "2023-09-01T09:40:29.810Z",
          "sentenceExpiryDate" to "2023-09-01T09:40:29.810Z",
          "sentenceSecondLength" to 1,
          "sentenceSecondLengthUnits" to "some text",
          "custodialTerm" to "some text",
          "extendedTerm" to "some text",
          "hasBeenReviewed" to false,
        ),
        "alternativesToRecallTried" to mapOf(
          "selected" to arrayListOf(
            "value" to "NONE",
            "details" to "detail",
          ),
          "allOptions" to arrayListOf(
            mapOf(
              "value" to "STANDARD",
              "text" to "Standard recall",
            ),
            mapOf(
              "value" to "FIXED_TERM",
              "text" to "Fixed term recall",
            ),
            mapOf(
              "value" to "NO_RECALL",
              "text" to "No recall",
            ),
          ),
        ),
        "licenceConditionsBreached" to mapOf(
          "standardLicenceConditions" to mapOf(
            "selected" to arrayListOf(
              "NO_OFFENCE",
              "NO_TRAVEL_OUTSIDE_UK",
            ),
            "allOptions" to arrayListOf(
              mapOf(
                "value" to "STANDARD",
                "text" to "Standard recall",
              ),
              mapOf(
                "value" to "FIXED_TERM",
                "text" to "Fixed term recall",
              ),
              mapOf(
                "value" to "NO_RECALL",
                "text" to "No recall",
              ),
            ),
          ),
          "additionalLicenceConditions" to mapOf(
            "selected" to arrayListOf(
              "ITEM, ITEM",
            ),
            "selectedOptions" to arrayListOf(
              mapOf(
                "mainCatCode" to "HASD",
                "subCatCode" to "HASD",
              ),
            ),
            "allOptions" to mapOf(
              "mainCatCode" to "HASD",
              "subCatCode" to "HASD",
              "title" to "title",
              "details" to "detail",
              "note" to "some text",
            ),
          ),
        ),
        "cvlLicenceConditionsBreached" to mapOf(
          "standardLicenceConditions" to mapOf(
            "selected" to arrayListOf(
              "NO_OFFENCE",
              "NO_TRAVEL_OUTSIDE_UK",
            ),
            "allOptions" to arrayListOf(
              mapOf(
                "code" to "STANDARD",
                "text" to "Standard recall",
              ),
              mapOf(
                "code" to "FIXED_TERM",
                "text" to "Fixed term recall",
              ),
              mapOf(
                "code" to "NO_RECALL",
                "text" to "No recall",
              ),
            ),
          ),
          "additionalLicenceConditions" to mapOf(
            "selected" to arrayListOf(
              "NO_OFFENCE",
              "NO_TRAVEL_OUTSIDE_UK",
            ),
            "allOptions" to arrayListOf(
              mapOf(
                "code" to "STANDARD",
                "text" to "Standard recall",
              ),
              mapOf(
                "code" to "FIXED_TERM",
                "text" to "Fixed term recall",
              ),
              mapOf(
                "code" to "NO_RECALL",
                "text" to "No recall",
              ),
            ),
          ),
          "bespokeLicenceConditions" to mapOf(
            "selected" to arrayListOf(
              "NO_OFFENCE",
              "NO_TRAVEL_OUTSIDE_UK",
            ),
            "allOptions" to arrayListOf(
              mapOf(
                "code" to "STANDARD",
                "text" to "Standard recall",
              ),
              mapOf(
                "value" to "FIXED_TERM",
                "text" to "Fixed term recall",
              ),
              mapOf(
                "code" to "NO_RECALL",
                "text" to "No recall",
              ),
            ),
          ),
        ),
        "additionalLicenceConditionsText" to "Example text",
        "vulnerabilities" to mapOf(
          "selected" to arrayListOf(
            mapOf(
              "value" to "some value",
              "details" to "some details",
            ),
          ),
          "allOptions" to arrayListOf(
            mapOf(
              "text" to "some text",
              "value" to "some value",
            ),
          ),
        ),
        "isUnderIntegratedOffenderManagement" to mapOf(
          "selected" to "string",
          "allOptions" to arrayListOf(
            mapOf(
              "text" to "some text",
              "value" to "some value",
            ),
          ),
        ),
        "indexOffenceDetails" to "string",
        "offenceDataFromLatestCompleteAssessment" to false,
        "offencesMatch" to true,
        "offenceAnalysis" to "string",
        "fixedTermAdditionalLicenceConditions" to mapOf(
          "selected" to true,
          "details" to "some details",
        ),
        "indeterminateOrExtendedSentenceDetails" to mapOf(
          "selected" to arrayListOf(
            mapOf(
              "value" to "some value",
              "details" to "some details",
            ),
          ),
          "allOptions" to arrayListOf(
            mapOf(
              "text" to "some text",
              "value" to "some value",
            ),
          ),
        ),
        "isMainAddressWherePersonCanBeFound" to mapOf(
          "selected" to true,
          "details" to "some details",
        ),
        "whyConsideredRecall" to mapOf(
          "selected" to "RISK_INCREASED",
          "allOptions" to arrayListOf(
            mapOf(
              "text" to "text",
              "value" to "value",
            ),
          ),
        ),
        "reasonsForNoRecall" to mapOf(
          "licenceBreach" to "string",
          "noRecallRationale" to "string",
          "popProgressMade" to "string",
          "popThoughts" to "string",
          "futureExpectations" to "string",
        ),
        "nextAppointment" to mapOf(
          "howWillAppointmentHappen" to mapOf(
            "selected" to "TELEPHONE",
            "allOptions" to arrayListOf(
              mapOf(
                "text" to "text",
                "value" to "value",
              ),
            ),
          ),
          "dateTimeOfAppointment" to "2023-09-01T09:40:29.810Z",
          "probationPhoneNumber" to "0834",
        ),
        "hasBeenReviewed" to mapOf(
          "personOnProbation" to true,
          "convictionDetail" to false,
          "mappa" to true,
        ),
        "previousReleases" to mapOf(
          "lastReleaseDate" to "2023-09-01T09:40:29.810Z",
          "lastReleasingPrisonOrCustodialEstablishment" to "string",
          "hasBeenReleasedPreviously" to false,
          "previousReleaseDates" to arrayListOf("2023-09-01T09:40:29.810Z", "2023-09-01T09:40:29.810Z"),
        ),
        "previousRecalls" to mapOf(
          "lastRecallDate" to "2023-09-01T09:40:29.810Z",
          "hasBeenRecalledPreviously" to false,
          "previousRecallDates" to arrayListOf("2023-09-01T09:40:29.810Z", "2023-09-01T09:40:29.810Z"),
        ),
        "recommendationStartedDomainEventSent" to false,
        "currentRoshForPartA" to mapOf(
          "riskToChildren" to "LOW",
          "riskToPublic" to "LOW",
          "riskToKnownAdult" to "LOW",
          "riskToStaff" to "LOW",
          "riskToPrisoners" to "LOW",
        ),
        "roshSummary" to mapOf(
          "natureOfRisk" to "risk",
          "whoIsAtRisk" to "person",
          "riskIncreaseFactors" to "factor",
          "riskMitigationFactors" to "mitigation",
          "riskImminence" to "none",
          "riskOfSeriousHarm" to mapOf(
            "overallRisk" to "low",
            "riskInCustody" to mapOf(
              "riskToChildren" to "LOW",
              "riskToPublic" to "LOW",
              "riskToKnownAdult" to "LOW",
              "riskToStaff" to "LOW",
              "riskToPrisoners" to "LOW",
            ),
            "riskInCommunity" to mapOf(
              "riskToChildren" to "LOW",
              "riskToPublic" to "LOW",
              "riskToKnownAdult" to "LOW",
              "riskToStaff" to "LOW",
              "riskToPrisoners" to "LOW",
            ),
          ),
          "lastUpdatedDate" to "2023-09-01T09:40:29.810Z",
          "error" to "error",
        ),
        "countersignSpoTelephone" to "null",
        "countersignSpoExposition" to "null",
        "countersignAcoExposition" to "null",
        "countersignAcoTelephone" to "null",
        "whoCompletedPartA" to mapOf(
          "name" to "name",
          "email" to "email",
          "telephone" to "08325",
          "region" to "LEI",
          "localDeliveryUnit" to "delivery",
          "isPersonProbationPractitionerForOffender" to true,
        ),
        "practitionerForPartA" to mapOf(
          "name" to "name",
          "email" to "email",
          "telephone" to "08325",
          "region" to "LEI",
          "localDeliveryUnit" to "delivery",
        ),
        "revocationOrderRecipients" to arrayListOf(
          "string",
          "another string",
        ),
        "decisionDateTime" to "2023-09-01T09:40:29.810Z",
        "ppcsQueryEmails" to arrayListOf(
          "string",
          "another string",
        ),
        "prisonOffender" to mapOf(
          "locationDescription" to "desc",
          "bookingNo" to "109",
          "facialImageId" to 1208413,
          "firstName" to "joe",
          "middleName" to "thomas",
          "lastName" to "bloggs",
          "dateOfBirth" to "2023-09-01T09:40:29.810Z",
          "status" to "status",
          "gender" to "M",
          "ethnicity" to "string",
          "cro" to "string",
          "pnc" to "string",
          "image" to "string",
        ),
        "prisonApiLocationDescription" to "null",
        "releaseUnderECSL" to false,
        "dateOfRelease" to "2023-09-01T09:40:29.810Z",
        "conditionalReleaseDate" to "2023-09-01T09:40:29.810Z",
        "nomisIndexOffence" to mapOf(
          "selected" to 1,
          "allOptions" to arrayListOf(
            mapOf(
              "offenderChargeId" to 1,
              "offenceCode" to "string",
              "offenceStatute" to "string",
              "offenceDescription" to "string",
              "offenceDate" to "2023-09-01T09:40:29.810Z",
              "sentenceDate" to "2023-09-01T09:40:29.810Z",
              "courtDescription" to "string",
              "sentenceStartDate" to "2023-09-01T09:40:29.810Z",
              "sentenceEndDate" to "2023-09-01T09:40:29.810Z",
              "bookingId" to 1,
              "terms" to arrayListOf(
                mapOf(
                  "years" to 1,
                  "months" to 2,
                  "weeks" to 4,
                  "days" to 16,
                  "code" to "code",
                ),
              ),
              "sentenceTypeDescription" to "string",
              "releaseDate" to "2023-09-01T09:40:29.810Z",
              "releasingPrison" to "string",
              "licenceExpiryDate" to "2023-09-01T09:40:29.810Z",
            ),
          ),
        ),
        "bookRecallToPpud" to mapOf(
          "decisionDateTime" to "2023-09-01T09:40:29.810Z",
          "custodyType" to "string",
          "releasingPrison" to "string",
          "indexOffence" to "string",
          "ppudSentenceId" to "string",
          "mappaLevel" to "string",
          "policeForce" to "string",
          "probationArea" to "string",
          "receivedDateTime" to "2023-09-01T09:40:29.810Z",
          "sentenceDate" to "2023-09-01T09:40:29.810Z",
          "gender" to "string",
          "ethnicity" to "string",
          "firstNames" to "string",
          "lastName" to "string",
          "dateOfBirth" to "2023-09-01T09:40:29.810Z",
          "cro" to "string",
          "prisonNumber" to "string",
          "legislationReleasedUnder" to "string",
          "minute" to "string",
        ),
        "ppudOffender" to mapOf(
          "id" to "string",
          "croOtherNumber" to "string",
          "dateOfBirth" to "string",
          "ethnicity" to "string",
          "familyName" to "string",
          "firstNames" to "string",
          "gender" to "string",
          "immigrationStatus" to "string",
          "nomsId" to "string",
          "prisonerCategory" to "string",
          "prisonNumber" to "string",
          "sentences" to arrayListOf(
            mapOf(
              "id" to "string",
              "offenceDescription" to "string",
              "sentenceExpiryDate" to "string",
              "dateOfSentence" to "string",
              "custodyType" to "string",
              "mappaLevel" to "string",
              "licenceExpiryDate" to "string",
              "offence" to mapOf(
                "indexOffence" to "offence",
                "dateOfIndexOffence" to "2023-03-03",
              ),
              "releases" to arrayListOf(
                mapOf(
                  "category" to "category",
                  "dateOfRelease" to "2020-02-02",
                  "releasedFrom" to "LEI",
                  "releasedUnder" to "string",
                  "releaseType" to "release",
                ),
              ),
              "sentenceLength" to mapOf(
                "partYears" to 2,
                "partMonths" to 4,
                "partDays" to 6,
              ),
              "sentencingCourt" to "string",
            ),
          ),
          "status" to "string",
          "youngOffender" to "string",
        ),
        "bookingMemento" to mapOf(
          "stage" to "string",
          "offenderId" to "string",
          "sentenceId" to "string",
          "releaseId" to "string",
          "recallId" to "string",
          "failed" to true,
          "failedMessage" to "string",
          "uploaded" to arrayListOf(
            "string1",
            "string2",
          ),
        ),
        "isOver18" to true,
        "isUnder18" to false,
        "isMappaLevelAbove1" to true,
        "isSentenceUnder12Months" to false,
        "isSentence12MonthsOrOver" to true,
        "hasBeenConvictedOfSeriousOffence" to false,
        "userNamePartACompletedBy" to "null",
        "userEmailPartACompletedBy" to "null",
        "lastPartADownloadDateTime" to "2023-09-01T09:40:29.810Z",
        "countersignSpoDateTime" to "2023-09-01T09:40:29.810Z",
        "countersignSpoName" to "null",
        "acoCounterSignEmail" to "null",
        "spoCounterSignEmail" to "null",
        "countersignAcoName" to "null",
        "countersignAcoDateTime" to "2023-09-01T09:40:29.810Z",
        "deleted" to false,
      ),
    ),
  )
}
