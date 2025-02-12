package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService

class GeneratePdfServiceApprovedPremisesTest : BaseGeneratePdfTest() {

  private fun writeAndThenReadPdf(testInput: List<Map<String, Map<String, List<Any?>>>>?): PdfDocument {
    val testFileName = "dummy-template-approved-premises.pdf"
    val serviceList = listOf(DpsService(name = "hmpps-approved-premises-api", content = testInput))
    generateSubjectAccessRequestPdf(testFileName, serviceList)
    return getGeneratedPdfDocument(testFileName)
  }

  @Test
  fun `generatePdfService renders for Approved Premises API with no data held`() {
    writeAndThenReadPdf(null).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Approved Premises")
      assertThat(text).doesNotContain("Short term accomodation")
      assertThat(text).contains("No Data Held")
    }
  }

  @Test
  fun `generatePdfService renders for Approved Premises API with data missing`() {
    val testInput = arrayListOf(
      mapOf(
        "ShortTermAccommodation" to mapOf(
          "StatusUpdateDetails" to arrayListOf(
            mapOf(
              "noms_number" to "A1234AI",
              "status_label" to "More information requested",
              "created_at" to "2024-11-26 12:34:00",
              "assessment_id" to "4920f481-abf0-4e9d-a514-d1b45cead392",
              "detail_label" to "Health needs",
              "application_id" to "edd787eb-31a3-4fad-a473-9cf8969f1487",
              "crn" to "X320741",
              "status_update_id" to "e9d0614a-a119-44e2-b488-bd485f0fcc1a",
            ),
          ),
          "Applications" to arrayListOf(
            mapOf(
              "data" to mapOf(
                "address-history" to mapOf(
                  "previous-address" to mapOf(
                    "hasPreviousAddress" to "yes",
                    "previousAddressLine2" to "",
                    "previousAddressLine1" to null,
                    "previousPostcode" to null,
                    "previousTownOrCity" to null,
                    "previousCounty" to "",
                  ),
                ),
                "offending-history" to mapOf(
                  "any-previous-convictions" to mapOf(
                    "hasAnyPreviousConvictions" to "yesRelevantRisk",
                  ),
                  "offence-history-data" to emptyList<Any?>(),
                ),
                "risk-to-self" to mapOf(
                  "historical-risk" to null,
                  "additional-information" to mapOf(
                    "additionalInformationDetail" to "",
                    "hasAdditionalInformation" to "no",
                  ),
                  "current-risk" to mapOf(
                    "confirmation" to "confirmed",
                    "currentRiskDetail" to "[R8.1.1] Review 06.10.21:\r\n\r\n        There have been numerous ACCTs opened since 2013 and every subsequent year he has been in custody.  In 2021...",
                  ),
                ),
                "cpp-details-and-hdc-licence-conditions" to mapOf(
                  "cpp-details" to mapOf(
                    "probationRegion" to "Leeds",
                    "name" to "Name Smith",
                    "telephone" to "01234 777 099",
                    "email" to "cpp@justice.example.com",
                  ),
                ),
                "confirm-consent" to mapOf(
                  "confirm-consent" to mapOf(
                    "hasGivenConsent" to "yes",
                    "consentDate-year" to "2023",
                    "consentDate-month" to "1",
                    "consentDate-day" to "1",
                    "consentDate" to "2023-01-01",
                  ),
                ),
                "funding-information" to mapOf(
                  "national-insurance" to mapOf(
                    "nationalInsuranceNumber" to "NS 123 288 12",
                  ),
                  "funding-source" to mapOf(
                    "fundingSource" to "personalSavings",
                  ),
                ),
                "confirm-eligibility" to mapOf(
                  "confirm-eligibility" to mapOf(
                    "isEligible" to "yes",
                  ),
                ),
                "current-offences" to mapOf(
                  "current-offence-data" to null,
                ),
                "personal-information" to mapOf(
                  "working-mobile-phone" to mapOf(
                    "working-mobile-phone" to "no",
                    "mobilePhoneNumber" to null,
                    "isSmartPhone" to null,
                  ),
                  "immigration-status" to mapOf(
                    "immigrationStatus" to "UK citizen",
                  ),
                  "pregnancy-information" to mapOf(
                    "isPregnant" to "no",
                  ),
                  "support-worker-preference" to mapOf(
                    "supportWorkerPreference" to "female",
                    "hasSupportWorkerPreference" to "yes",
                  ),
                ),
              ),
              "submitted_at" to "2023-11-12T09:00:00+00:00",
              "conditional_release_date" to "2025-01-26",
              "telephone_number" to "0800 123 456",
              "created_at" to null,
              "abandoned_at" to null,
              "referring_prison_code" to null,
              "created_by_user" to "Name Smith (Fake)",
              "noms_number" to "A1234AI",
              "preferred_areas" to "Bristol | Newcastle",
              "id" to "edd787eb-31a3-4fad-a473-9cf8969f1487",
              "crn" to "X320741",
              "hdc_eligibility_date" to "2024-11-26",
            ),
          ),
          "Assessments" to emptyList<Any?>(),
        ),
      ),
    )

    writeAndThenReadPdf(testInput).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Approved Premises")
      assertThat(text).contains("Short term accommodation")
    }
  }

  @Test
  fun `generatePdfService renders for Approved Premises API`() {
    writeAndThenReadPdf(serviceData).use {
      val text = PdfTextExtractor.getTextFromPage(it.getPage(2))
      assertThat(text).contains("Approved Premises")
      assertThat(text).contains("Short term accommodation")
      assertThat(text).contains("Applications")
      val thirdPageText = PdfTextExtractor.getTextFromPage(it.getPage(4))
      assertThat(thirdPageText).contains("Offence")
      assertThat(thirdPageText).contains("Arson")
      assertThat(thirdPageText).contains("05 June 2020")
    }
  }

  val serviceData = arrayListOf(
    mapOf(
      "ShortTermAccommodation" to mapOf(
        "StatusUpdateDetails" to arrayListOf(
          mapOf(
            "noms_number" to "A1234AI",
            "status_label" to "More information requested",
            "created_at" to "2024-11-26 12:34:00",
            "assessment_id" to "4920f481-abf0-4e9d-a514-d1b45cead392",
            "detail_label" to "Health needs",
            "application_id" to "edd787eb-31a3-4fad-a473-9cf8969f1487",
            "crn" to "X320741",
            "status_update_id" to "e9d0614a-a119-44e2-b488-bd485f0fcc1a",
          ),
        ),
        "Applications" to arrayListOf(
          mapOf(
            "data" to mapOf(
              "address-history" to mapOf(
                "previous-address" to mapOf(
                  "hasPreviousAddress" to "yes",
                  "previousAddressLine2" to "",
                  "previousAddressLine1" to "12 The Street",
                  "previousPostcode" to "LU1 2BQ",
                  "previousTownOrCity" to "Luton",
                  "previousCounty" to "",
                ),
              ),
              "offending-history" to mapOf(
                "any-previous-convictions" to mapOf(
                  "hasAnyPreviousConvictions" to "yesRelevantRisk",
                ),
                "offence-history-data" to arrayListOf(
                  mapOf(
                    "summary" to "Mr Bertrand entered the house of an associate John Smith on the night of Feb 20th, 2020 and took items of value including a laptop and flatscreen TV",
                    "offenceDate-month" to "2",
                    "titleAndNumber" to "Burglary in a dwelling - 02800",
                    "offenceDate-year" to "2020",
                    "offenceCategory" to "other",
                    "offenceDate-day" to "20",
                    "sentenceLength" to "6 months",
                  ),
                ),
              ),
              "risk-to-self" to mapOf(
                "historical-risk" to mapOf(
                  "historicalRiskDetail" to "[R8.1.4] Review 06.10.21:\r\n\r\n        There have been numerous ACCTs opened since 2013 and every subsequent year he has been in custody.  In 2021 there...",
                  "confirmation" to "confirmed",
                ),
                "oasys-import" to mapOf(
                  "oasysImportedDate" to "2023-11-28T11:42:22.183Z",
                  "oasysStartedDate" to "2023-11-20",
                  "oasysCompletedDate" to "2023-11-22",
                ),
                "additional-information" to mapOf(
                  "additionalInformationDetail" to "",
                  "hasAdditionalInformation" to "no",
                ),
                "vulnerability" to mapOf(
                  "vulnerabilityDetail" to "[R8.3.1] Review 06.10.21:\r\n\r\n        A previous assessor opined that Mr Smith was displaying all the characteristics of someone...",
                  "confirmation" to "confirmed",
                ),
                "current-risk" to mapOf(
                  "confirmation" to "confirmed",
                  "currentRiskDetail" to "[R8.1.1] Review 06.10.21:\r\n\r\n        There have been numerous ACCTs opened since 2013 and every subsequent year he has been in custody.  In 2021...",
                ),
              ),
              "cpp-details-and-hdc-licence-conditions" to mapOf(
                "cpp-details" to mapOf(
                  "probationRegion" to "Leeds",
                  "name" to "Name Smith",
                  "telephone" to "01234 777 099",
                  "email" to "cpp@justice.example.com",
                ),
                "non-standard-licence-conditions" to mapOf(
                  "nonStandardLicenceConditionsDetail" to "There are exclusion zones, please check",
                  "nonStandardLicenceConditions" to "yes",
                ),
              ),
              "information-needed-from-applicant" to mapOf(
                "information-needed-from-applicant" to mapOf(
                  "hasInformationNeeded" to "yes",
                ),
              ),
              "confirm-consent" to mapOf(
                "confirm-consent" to mapOf(
                  "hasGivenConsent" to "yes",
                  "consentDate-year" to "2023",
                  "consentDate-month" to "1",
                  "consentDate-day" to "1",
                  "consentDate" to "2023-01-01",
                ),
              ),
              "funding-information" to mapOf(
                "national-insurance" to mapOf(
                  "nationalInsuranceNumber" to "NS 123 288 12",
                ),
                "funding-source" to mapOf(
                  "fundingSource" to "personalSavings",
                ),
              ),
              "confirm-eligibility" to mapOf(
                "confirm-eligibility" to mapOf(
                  "isEligible" to "yes",
                ),
              ),
              "current-offences" to mapOf(
                "current-offence-data" to arrayListOf(
                  mapOf(
                    "summary" to "summary detail",
                    "offenceDate-month" to "6",
                    "titleAndNumber" to "Arson (09000)",
                    "outstandingCharges" to "yes",
                    "offenceDate-year" to "2020",
                    "offenceCategory" to "Arson",
                    "offenceDate-day" to "5",
                    "sentenceLength" to "3 years",
                    "outstandingChargesDetail" to "The offence took place at the home of his ex-partner in Liverpool",
                  ),
                  mapOf(
                    "summary" to "This offence is against a victim living in Liverpool, see exclusion zones.",
                    "offenceDate-month" to "7",
                    "titleAndNumber" to "Stalking (08000)",
                    "outstandingCharges" to "no",
                    "offenceDate-year" to "2023",
                    "offenceCategory" to "Stalking",
                    "offenceDate-day" to "6",
                    "sentenceLength" to "2 months",
                  ),
                ),
              ),
              "personal-information" to mapOf(
                "working-mobile-phone" to mapOf(
                  "working-mobile-phone" to "yes",
                  "mobilePhoneNumber" to "07777 777 777",
                  "isSmartPhone" to "yes",
                ),
                "immigration-status" to mapOf(
                  "immigrationStatus" to "UK citizen",
                ),
                "pregnancy-information" to mapOf(
                  "dueDate-month" to "6",
                  "dueDate-year" to "2024",
                  "dueDate-day" to "5",
                  "isPregnant" to "yes",
                ),
                "support-worker-preference" to mapOf(
                  "supportWorkerPreference" to "female",
                  "hasSupportWorkerPreference" to "yes",
                ),
              ),
              "check-your-answers" to mapOf(
                "check-your-answers" to mapOf(
                  "checkYourAnswers" to "confirmed",
                ),
              ),
              "equality-and-diversity-monitoring" to mapOf(
                "care-leaver" to mapOf(
                  "isCareLeaver" to "no",
                ),
                "military-veteran" to mapOf(
                  "isVeteran" to "no",
                ),
                "sex-and-gender" to mapOf(
                  "gender" to "yes",
                  "sex" to "male",
                  "optionalGenderIdentity" to "",
                ),
                "ethnic-group" to mapOf(
                  "ethnicGroup" to "white",
                ),
                "parental-carer-responsibilities" to mapOf(
                  "hasParentalOrCarerResponsibilities" to "yes",
                ),
                "disability" to mapOf(
                  "hasDisability" to "preferNotToSay",
                  "otherDisability" to "",
                ),
                "white-background" to mapOf(
                  "optionalWhiteBackground" to "",
                  "whiteBackground" to "english",
                ),
                "sexual-orientation" to mapOf(
                  "orientation" to "preferNotToSay",
                  "otherOrientation" to "",
                ),
                "marital-status" to mapOf(
                  "maritalStatus" to "neverMarried",
                ),
                "will-answer-equality-questions" to mapOf(
                  "willAnswer" to "yes",
                ),
                "religion" to mapOf(
                  "religion" to "atheist",
                ),
              ),
              "risk-of-serious-harm" to mapOf(
                "summary" to mapOf(
                  "additionalComments" to "",
                ),
                "reducing-risk" to mapOf(
                  "factorsLikelyToReduceRisk" to "[R10.5] Engage with Mental Health Services in prison and in the community.\r\nMaintain emotional stability.\r\nAbstain from alcohol.\r\nAbstain from illegal drugs.\r\nRegular testing for alcohol and drug use, in prison and on Licence.\r\nMaintain stable accommodation...",
                  "confirmation" to "confirmed",
                ),
                "oasys-import" to mapOf(
                  "oasysImportedDate" to "2023-11-28T11:43:06.137Z",
                ),
                "cell-share-information" to mapOf(
                  "hasCellShareComments" to "no",
                  "cellShareInformationDetail" to "",
                ),
                "summary-data" to mapOf(
                  "oasysImportedDate" to "2023-11-28T11:42:22.183Z",
                  "oasysStartedDate" to "2023-11-20",
                  "oasysCompletedDate" to "2023-11-22",
                  "value" to mapOf(
                    "lastUpdated" to "2022-11-02",
                    "overallRisk" to "High",
                    "riskToChildren" to "Medium",
                    "riskToPublic" to "High",
                    "riskToKnownAdult" to "High",
                    "riskToStaff" to "Low",
                  ),
                  "status" to "retrieved",
                ),
                "risk-factors" to mapOf(
                  "circumstancesLikelyToIncreaseRisk" to "[R10.4] CIRCUMSTANCES:\r\nA lack of stable accommodation\r\nunemployment\r\nnon constructive use of time\r\nbreakdown of family support\r\nbeing unable to access benefits\r\n\r\nUNDERLYING FACTORS:\r\npro criminal attitudes supporting commission of crime...",
                  "whenIsRiskLikelyToBeGreatest" to "[R10.3] STATIC RISK FACTORS:\r\n\r\nMr Smith gender - At the time of the offence, Mr Smith was 35 years old and considered to be in the higher risk group.\r\n\r\nSTABLE DYNAMIC RISK FACTORS:\r\nThinking and behaviour - limited ability to manage mood or emotions poor impulse control. Mr Smith demonstrated cognitive deficit in committing the index offences and this still remains a concern...",
                  "confirmation" to "confirmed",
                ),
                "risk-management-arrangements" to mapOf(
                  "maracDetails" to "",
                  "mappaDetails" to "",
                  "iomDetails" to "",
                  "arrangements" to "no",
                ),
                "additional-risk-information" to mapOf(
                  "additionalInformationDetail" to "",
                  "hasAdditionalInformation" to "no",
                ),
                "risk-to-others" to mapOf(
                  "whoIsAtRisk" to "[R10.1] IN CUSTODY\r\n\r\nKNOWN ADULTS:\r\nSuch as Ms Name Underhill and any of the victims of the index offence if they are placed close to Mr Smith cell.\r\n\r\nCHILDREN:\r\nOne Smith, Two Smith, Three Smith",
                  "natureOfRisk" to "[R10.2] IN CUSTODY:\r\n\r\nKNOWN ADULTS:\r\nIntimidation, threats of violence, use of weapons or boiling water, physical education and violent assault, long term psychological impact as a result of Mr Smith violent behaviour. This harm may be cause in the course of physical altercation due to seeking revenge or holding grudges...",
                  "confirmation" to "confirmed",
                ),
              ),
              "hdc-licence-dates" to mapOf(
                "hdc-licence-dates" to mapOf(
                  "hdcEligibilityDate-day" to "18",
                  "hdcEligibilityDate-month" to "10",
                  "conditionalReleaseDate-day" to "18",
                  "hdcEligibilityDate-year" to "2024",
                  "conditionalReleaseDate" to "2024-12-18",
                  "conditionalReleaseDate-year" to "2024",
                  "conditionalReleaseDate-month" to "12",
                  "hdcEligibilityDate" to "2024-10-18",
                ),
              ),
              "area-information" to mapOf(
                "family-accommodation" to mapOf(
                  "familyProperty" to "yes",
                ),
                "second-preferred-area" to mapOf(
                  "preferenceReason" to "Mr Bertrand has a sister in Hertford",
                  "preferredArea" to "Hertford",
                ),
                "exclusion-zones" to mapOf(
                  "hasExclusionZones" to "yes",
                  "exclusionZonesDetail" to "Avoid Liverpool",
                ),
                "first-preferred-area" to mapOf(
                  "preferenceReason" to "This is where Mr Bertrand has spent the majority of his life and where his family live",
                  "preferredArea" to "Luton",
                ),
                "gang-affiliations" to mapOf(
                  "gangOperationArea" to "Derby",
                  "gangName" to "Gang name",
                  "rivalGangDetail" to "Rival gang detail",
                  "hasGangAffiliations" to "yes",
                ),
              ),
              "health-needs" to mapOf(
                "physical-health" to mapOf(
                  "indyLivingDetail" to "",
                  "needsDetail" to "Mr Bertrand is in a wheelchair",
                  "canClimbStairs" to "no",
                  "addSupportDetail" to "Mr Bertrand needs assistance with getting to appointments",
                  "isReceivingMedicationOrTreatment" to "no",
                  "canLiveIndependently" to "yes",
                  "hasPhyHealthNeeds" to "yes",
                  "medicationOrTreatmentDetail" to "",
                  "requiresAdditionalSupport" to "yes",
                ),
                "mental-health" to mapOf(
                  "needsDetail" to "Mr Bertrand has a history of schizophrenic episodes and also suffers from epilepsy",
                  "cantManageMedicationNotes" to "",
                  "isEngagedWithCommunity" to "no",
                  "medicationIssues" to "",
                  "needsPresentation" to "He presents with withdrawal and occasionally erratic behaviour.",
                  "hasMentalHealthNeeds" to "yes",
                  "isEngagedWithServicesInCustody" to "yes",
                  "canManageMedicationNotes" to "",
                  "canManageMedication" to "notPrescribedMedication",
                ),
                "substance-misuse" to mapOf(
                  "usesIllegalSubstances" to "yes",
                  "releasedWithNaloxone" to "yes",
                  "substituteMedicationDetail" to "Methadone",
                  "pastSubstanceMisuse" to "yes",
                  "requiresSubstituteMedication" to "yes",
                  "substanceMisuse" to "Mr Bertrand is a heroin user",
                  "pastSubstanceMisuseDetail" to "Mr Bertrand frequently used heroin",
                  "engagedWithDrugAndAlcoholService" to "yes",
                  "drugAndAlcoholServiceDetail" to "Drug Awareness Service",
                  "intentToReferToServiceOnRelease" to "yes",
                ),
                "learning-difficulties" to mapOf(
                  "needsDetail" to "Mr Bertrand has been diagnosed with ADHD and has difficulty absorbing written information.",
                  "addSupportDetail" to "",
                  "interactionDetail" to "",
                  "vulnerabilityDetail" to "",
                  "isVulnerable" to "no",
                  "hasLearningNeeds" to "yes",
                  "hasDifficultyInteracting" to "no",
                  "requiresAdditionalSupport" to "no",
                ),
                "other-health" to mapOf(
                  "hasLongTermHealthCondition" to "yes",
                  "healthConditionDetail" to "Mr Bertrand has some complex vascular and circulatory issues which have contributed to his lack of mobility",
                  "hasHadStroke" to "no",
                  "hasSeizures" to "yes",
                  "seizuresDetail" to "He has intermittent epilepsy which is well controlled by carbamazepine",
                  "beingTreatedForCancer" to "no",
                ),
                "brain-injury" to mapOf(
                  "injuryDetail" to "",
                  "addSupportDetail" to "",
                  "interactionDetail" to "",
                  "vulnerabilityDetail" to "",
                  "isVulnerable" to "no",
                  "hasDifficultyInteracting" to "no",
                  "hasBrainInjury" to "no",
                  "requiresAdditionalSupport" to "no",
                ),
                "communication-and-language" to mapOf(
                  "supportDetail" to "",
                  "requiresInterpreter" to "no",
                  "interpretationDetail" to "",
                  "hasSupportNeeds" to "no",
                  "hasCommunicationNeeds" to "no",
                  "communicationDetail" to "",
                ),
              ),
              "referrer-details" to mapOf(
                "job-title" to mapOf(
                  "jobTitle" to "POM",
                ),
                "contact-number" to mapOf(
                  "telephone" to "07777 777 777",
                ),
                "confirm-details" to mapOf(
                  "name" to "Name Smith",
                  "email" to "rsmith@justice.example.com",
                ),
              ),
            ),
            "submitted_at" to "2023-11-12T09:00:00+00:00",
            "conditional_release_date" to "2025-01-26",
            "telephone_number" to "0800 123 456",
            "created_at" to "2023-11-10T09:00:00+00:00",
            "abandoned_at" to null,
            "referring_prison_code" to "LEI",
            "created_by_user" to "Name Smith (Fake)",
            "noms_number" to "A1234AI",
            "preferred_areas" to "Bristol | Newcastle",
            "id" to "edd787eb-31a3-4fad-a473-9cf8969f1487",
            "crn" to "X320741",
            "hdc_eligibility_date" to "2024-11-26",
          ),
        ),
        "Assessments" to arrayListOf(
          mapOf(
            "noms_number" to "A1234AI",
            "created_at" to "2024-11-26T12:29:21.489538+00:00",
            "id" to "3fc30355-2cd3-45cf-81c4-0f800e5f07b6",
            "assessor_name" to null,
            "nacro_referral_id" to null,
            "application_id" to "d1f0485b-322a-4127-931f-b0de7d47a9ca",
            "crn" to "X320741",
          ),
        ),
      ),
    ),
  )
}
