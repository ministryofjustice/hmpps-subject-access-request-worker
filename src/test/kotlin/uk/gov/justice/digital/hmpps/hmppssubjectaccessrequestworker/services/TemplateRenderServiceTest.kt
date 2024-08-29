package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain

class TemplateRenderServiceTest : DescribeSpec(
  {
    describe("getServiceTemplate") {
      it("returns a test service template") {
        val templateRenderService = TemplateRenderService()
        val testTemplate = templateRenderService.getServiceTemplate("test-service")
        testTemplate.shouldNotBeNull()
        testTemplate.shouldContain("Test Data")
      }

      it("returns null if service template does not exist") {
        val templateRenderService = TemplateRenderService()
        val testTemplate = templateRenderService.getServiceTemplate("fake-service")
        testTemplate.shouldBeNull()
      }
    }

    describe("getStyleTemplate") {
      it("returns the style template which contains a reference to the service template") {
        val templateRenderService = TemplateRenderService()
        val testTemplate = templateRenderService.getStyleTemplate()
        testTemplate.shouldNotBeNull()
        testTemplate.shouldContain("{{ serviceTemplate }}")
      }
    }

    describe("renderTemplate") {
      it("renders a style template given a service template") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "testKey" to "testValue",
            "moreData" to mapOf(
              "nestedKey" to "nestedValue",
            ),
            "arrayData" to arrayListOf(
              "arrayValue1-1",
              "arrayValue1-2",
            ),
          ),
          mapOf(
            "testKey" to "testValue2",
            "moreData" to mapOf(
              "nestedKey" to "nestedValue2",
            ),
            "arrayData" to arrayListOf(
              "arrayValue2-1",
              "arrayValue2-2",
            ),
          ),
        )
        val renderedStyleTemplate = templateRenderService.renderTemplate("test-service", testServiceData)
        renderedStyleTemplate.shouldNotBeNull()
        renderedStyleTemplate.shouldContain("<style>")
        renderedStyleTemplate.shouldContain("</style>")
        renderedStyleTemplate.shouldContain("<td>Test Key:</td><td>testValue</td>")
        renderedStyleTemplate.shouldContain("<td>Nested Data:</td><td>nestedValue</td>")
        renderedStyleTemplate.shouldContain("<td>Array Data:</td><td><ul><li>arrayValue1-1</li><li>arrayValue1-2</li></ul></td>")
        renderedStyleTemplate.shouldContain("<td>Test Key:</td><td>testValue2</td>")
        renderedStyleTemplate.shouldContain("<td>Nested Data:</td><td>nestedValue2</td>")
        renderedStyleTemplate.shouldContain("<td>Array Data:</td><td><ul><li>arrayValue2-1</li><li>arrayValue2-2</li></ul></td>")
      }
    }

    describe("keyworkerTemplate") {
      it("renders a template given a keyworker template") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "offenderKeyworkerId" to 12912,
            "offenderNo" to "A1234AA",
            "staffId" to 485634,
            "assignedDateTime" to "2019-12-03T11:00:58.21264",
            "active" to false,
            "allocationReason" to "MANUAL",
            "allocationType" to "M",
            "userId" to "JROBERTSON_GEN",
            "prisonId" to "MDI",
            "expiryDateTime" to "2020-12-02T16:31:01",
            "deallocationReason" to "RELEASED",
            "creationDateTime" to "2019-12-03T11:00:58.213527",
            "createUserId" to "JROBERTSON_GEN",
            "modifyDateTime" to "2020-12-02T16:31:32.128317",
            "modifyUserId" to "JROBERTSON_GEN",
          ),
        )
        val renderedStyleTemplate = templateRenderService.renderTemplate("keyworker-api", testServiceData)
        renderedStyleTemplate.shouldNotBeNull()
        renderedStyleTemplate.shouldContain("<style>")
        renderedStyleTemplate.shouldContain("</style>")
        renderedStyleTemplate.shouldContain("<td class=\"data-column-40\">Offender Keyworker ID</td><td>12912</td>")
        renderedStyleTemplate.shouldContain("<td>Allocation reason</td><td>MANUAL</td>")
        renderedStyleTemplate.shouldContain("<td>Creation date</td><td>03 December 2019, 11:00:58 am</td>")
      }
    }

    describe("activitiesTemplate") {
      it("renders a template given an activities template") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> = arrayListOf(
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

        val renderedStyleTemplate = templateRenderService.renderTemplate("hmpps-activities-management-api", testServiceData)
        renderedStyleTemplate.shouldNotBeNull()
        renderedStyleTemplate.shouldContain("<style>")
        renderedStyleTemplate.shouldContain("</style>")
        renderedStyleTemplate.shouldContain("<td>Prisoner number</td><td>A4743DZ</td>")
        renderedStyleTemplate.shouldContain("<td class=\"data-column-25\">End date</td>")
        renderedStyleTemplate.shouldContain("<td class=\"data-column-50\">Count</td>")
        renderedStyleTemplate.shouldContain("<h3>Application - Waiting list ID 1</h3>")
        renderedStyleTemplate.shouldContain("<h3>Application - Waiting list ID 10</h3>")
        renderedStyleTemplate.shouldContain("<td class=\"data-column-25\">Status date</td>")
        renderedStyleTemplate.shouldContain("<h3>Appointment - ID 18305</h3>")
        renderedStyleTemplate.shouldContain("<h3>Appointment - ID 16340</h3>")
      }
    }

    describe("incentivesTemplate") {
      it("renders a template given an incentives template") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "id" to 2898970,
            "bookingId" to "1208204",
            "prisonerNumber" to "A485634",
            "nextReviewDate" to "2019-12-03",
            "levelCode" to "ENH",
            "prisonId" to "UAL",
            "locationId" to "M-16-15",
            "reviewTime" to "2023-07-03T21:14:25.059172",
            "reviewedBy" to "MDI",
            "commentText" to "comment",
            "current" to true,
            "reviewType" to "REVIEW",
          ),
        )
        val renderedStyleTemplate = templateRenderService.renderTemplate("hmpps-incentives-api", testServiceData)
        renderedStyleTemplate.shouldNotBeNull()
        renderedStyleTemplate.shouldContain("<style>")
        renderedStyleTemplate.shouldContain("</style>")
        renderedStyleTemplate.shouldContain("<td>Booking ID</td><td>1208204</td>")
        renderedStyleTemplate.shouldContain("<td>Next review date</td><td>03 December 2019</td>")
        renderedStyleTemplate.shouldContain("<td>Review time</td><td>03 July 2023, 9:14:25 pm</td>")
        renderedStyleTemplate.shouldContain("<td>Current</td><td>true</td>")
      }
    }

    describe("adjudicationsTemplate") {
      it("renders a template given an adjudications template") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> =
          arrayListOf(
            mapOf(
              "chargeNumber" to "1525733",
              "prisonerNumber" to "A3863DZ",
              "gender" to "FEMALE",
              "incidentDetails" to mapOf(
                "locationId" to 26149,
                "dateTimeOfIncident" to "2023-06-08T12:00:00",
                "dateTimeOfDiscovery" to "2023-06-08T12:00:00",
                "handoverDeadline" to "2023-06-10T12:00:00",
              ),
              "isYouthOffender" to false,
              "incidentRole" to mapOf(
                "roleCode" to "25c",
                "offenceRule" to mapOf(
                  "paragraphNumber" to "25(c)",
                  "paragraphDescription" to "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:",
                ),
                "associatedPrisonersNumber" to "A3864DZ",
              ),
              "offenceDetails" to mapOf(
                "offenceCode" to 16001,
                "offenceRule" to mapOf(
                  "paragraphNumber" to "16",
                  "paragraphDescription" to "Intentionally or recklessly sets fire to any part of a prison or any other property, whether or not her own",
                  "nomisCode" to "51:16",
                  "withOthersNomisCode" to "51:25C",
                ),
                "protectedCharacteristics" to mapOf(
                  "id" to 247,
                  "characteristic" to "AGE",
                ),
              ),
              "incidentStatement" to mapOf(
                "statement" to "Vera incited Brian Duckworth to set fire to a lamp\r\ndamages - the lamp\r\nevidence includes something in a bag with a reference number of 1234\r\nwitnessed by amarktest",
                "completed" to true,
              ),
              "createdByUserId" to "LBENNETT_GEN",
              "createdDateTime" to "2023-06-08T14:17:20.831884",
              "status" to "CHARGE_PROVED",
              "reviewedByUserId" to "AMARKE_GEN",
              "statusReason" to "",
              "statusDetails" to "",
              "damages" to arrayListOf(
                mapOf(
                  "code" to "ELECTRICAL_REPAIR",
                  "details" to "mend a lamp",
                  "reporter" to "LBENNETT_GEN",
                ),
              ),
              "evidence" to arrayListOf(
                mapOf(
                  "code" to "BAGGED_AND_TAGGED",
                  "identifier" to "1234",
                  "details" to "evidence in a bag with a reference number",
                  "reporter" to "LBENNETT_GEN",
                ),
              ),
              "witnesses" to arrayListOf(
                mapOf(
                  "code" to "OFFICER",
                  "firstName" to "Andrew",
                  "lastName" to "Marke",
                  "reporter" to "LBENNETT_GEN",
                ),
              ),
              "hearings" to arrayListOf(
                mapOf(
                  "id" to 467,
                  "locationId" to 775,
                  "dateTimeOfHearing" to "2023-06-08T14:25:00",
                  "oicHearingType" to "INAD_ADULT",
                  "outcome" to mapOf(
                    "id" to 534,
                    "adjudicator" to "James Warburton",
                    "code" to "COMPLETE",
                    "plea" to "GUILTY",
                  ),
                  "agencyId" to "MDI",
                ),
              ),
              "disIssueHistory" to arrayListOf(
                mapOf(
                  "issuingOfficer" to "someone",
                  "dateTimeOfIssue" to "2023-06-08T14:25:00",
                ),
              ),
              "dateTimeOfFirstHearing" to "2023-06-08T14:25:00",
              "outcomes" to arrayListOf(
                mapOf(
                  "hearing" to mapOf(
                    "id" to 467,
                    "locationId" to 775,
                    "dateTimeOfHearing" to "2023-06-08T14:25:00",
                    "oicHearingType" to "INAD_ADULT",
                    "outcome" to mapOf(
                      "id" to 534,
                      "adjudicator" to "James Warburton",
                      "code" to "COMPLETE",
                      "plea" to "GUILTY",
                    ),
                    "agencyId" to "MDI",
                  ),
                  "outcome" to mapOf(
                    "outcome" to mapOf(
                      "id" to 733,
                      "code" to "CHARGE_PROVED",
                      "canRemove" to true,
                    ),
                  ),
                ),
              ),
              "punishments" to arrayListOf(
                mapOf(
                  "id" to 241,
                  "type" to "PRIVILEGE",
                  "privilegeType" to "TV",
                  "schedule" to mapOf(
                    "days" to 7,
                    "duration" to 7,
                    "measurement" to "DAYS",
                    "startDate" to "2023-06-09",
                    "endDate" to "2023-06-16",
                  ),
                  "canRemove" to true,
                  "canEdit" to true,
                  "rehabilitativeActivities" to arrayListOf(
                    mapOf(
                      "id" to 241,
                      "details" to "Some info",
                      "monitor" to "yes",
                      "endDate" to "2023-06-09",
                      "totalSessions" to 16,
                      "completed" to true,
                    ),
                  ),
                ),
                mapOf(
                  "id" to 240,
                  "type" to "DAMAGES_OWED",
                  "schedule" to mapOf(
                    "days" to 0,
                    "duration" to 0,
                    "measurement" to "DAYS",
                  ),
                  "damagesOwedAmount" to 20,
                  "canRemove" to true,
                  "canEdit" to true,
                  "rehabilitativeActivities" to emptyList<Any>(),
                ),
              ),
              "punishmentComments" to mapOf(
                "id" to 1,
                "comment" to "test comment",
                "reasonForChange" to "APPEAL",
                "nomisCreatedBy" to "person",
                "actualCreatedDate" to "2023-06-16",
              ),
              "outcomeEnteredInNomis" to false,
              "originatingAgencyId" to "MDI",
              "linkedChargeNumbers" to arrayListOf("9872-1", "9872-2"),
              "canActionFromHistory" to false,
            ),
          )
        val renderedStyleTemplate =
          templateRenderService.renderTemplate("hmpps-manage-adjudications-api", testServiceData)
        renderedStyleTemplate.shouldNotBeNull()
        renderedStyleTemplate.shouldContain("<style>")
        renderedStyleTemplate.shouldContain("</style>")
        renderedStyleTemplate.shouldContain("<td>Prisoner number</td><td>A3863DZ</td>")
        renderedStyleTemplate.shouldContain("<td>Date and time of incident</td><td>08 June 2023, 12:00:00 pm</td>")
        renderedStyleTemplate.shouldContain("<td>Incident role code</td><td>25c</td>")
        renderedStyleTemplate.shouldContain("<td>Description</td><td>Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:</td>")
        renderedStyleTemplate.shouldContain("<td>Description</td><td>Intentionally or recklessly sets fire to any part of a prison or any other property, whether or not her own</td>")
        renderedStyleTemplate.shouldContain("<td>Completed</td><td>true</td>")
        renderedStyleTemplate.shouldContain("<td>Status</td><td>CHARGE_PROVED</td>")
        renderedStyleTemplate.shouldContain("<td>ELECTRICAL_REPAIR</td>")
        renderedStyleTemplate.shouldContain("<td>BAGGED_AND_TAGGED</td>")
        renderedStyleTemplate.shouldContain("<td>OIC hearing type</td><td>INAD_ADULT</td>")
        renderedStyleTemplate.shouldContain("<td>James Warburton</td>")
        renderedStyleTemplate.shouldContain("<td>Code</td><td>CHARGE_PROVED</td>")
        renderedStyleTemplate.shouldContain("<td>Privilege type</td><td>TV</td>")
        renderedStyleTemplate.shouldContain("<td>Linked charge numbers</td><td>[9872-1, 9872-2]</td>")
        renderedStyleTemplate.shouldContain("<td>DAYS</td>")
        renderedStyleTemplate.shouldContain("<td>Some info</td>")
        renderedStyleTemplate.shouldContain("<td>Reason for change</td><td>APPEAL</td>")
      }
    }

    describe("hdcTemplate") {
      it("renders a template given a home detentions curfew template") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
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
          ),
          mapOf(
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
                      "decisionMaker" to "Louise Norris",
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
          ),
          mapOf(
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
          ),
        )
        val renderedStyleTemplate = templateRenderService.renderTemplate("hmpps-hdc-api", testServiceData)
        renderedStyleTemplate.shouldNotBeNull()
        renderedStyleTemplate.shouldContain("<style>")
        renderedStyleTemplate.shouldContain("</style>")
        renderedStyleTemplate.shouldContain("<td>Booking ID</td><td>1108337</td>")
        renderedStyleTemplate.shouldContain("<td>Specific area</td><td>No</td>")
        renderedStyleTemplate.shouldContain("<td>Vary version</td><td>0</td>")
        renderedStyleTemplate.shouldContain("<td>Deleted at</td><td>15 March 2024, 11:11:14 am</td>")
        renderedStyleTemplate.shouldContain("<td>Has considered checks</td><td>Yes</td>")
        renderedStyleTemplate.shouldContain("<td>First night from</td><td>15:00</td>")
        renderedStyleTemplate.shouldContain("<td>Friday from</td><td>19:00</td>")
        renderedStyleTemplate.shouldContain("<td>Decision maker</td><td>Louise Norris</td>")
        renderedStyleTemplate.shouldContain("<td>Offence committed before Feb 2015</td><td>No</td>")
        renderedStyleTemplate.shouldContain("<td>Telephone</td><td>47450</td>")
        renderedStyleTemplate.shouldContain("<td>Address line 1</td><td>The Street</td>")
        renderedStyleTemplate.shouldContain("<td>Bass requested</td><td>Yes</td>")
        renderedStyleTemplate.shouldContain("<td>Bass area check seen</td><td>true</td>")
        renderedStyleTemplate.shouldContain("<td>Additional conditions required</td><td>No</td>")
        renderedStyleTemplate.shouldContain("<td>Action</td><td>UPDATE_SECTION</td>")
        renderedStyleTemplate.shouldContain("<td>Booking ID</td><td>1108337</td>")
      }
    }

    describe("useOfForceTemplate") {
      it("renders a template given a Use of Force template") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> = arrayListOf(
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
            "userId" to "ANDYLEE_ADM",
            "reporterName" to "Andrew Lee",
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
                  "name" to "Andrew Lee",
                  "email" to "andrew.lee@digital.justice.gov.uk",
                  "staffId" to 486084,
                  "username" to "ZANDYLEE_ADM",
                  "verified" to true,
                  "activeCaseLoadId" to "MDI",
                ),
                mapOf(
                  "name" to "Lee Andrew",
                  "email" to "lee.andrew@digital.justice.gov.uk",
                  "staffId" to 486084,
                  "username" to "AMD_LEE",
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
                    "name" to "Andrew Lee",
                  ),
                  mapOf(
                    "name" to "Andrew Leedsd",
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
                "userId" to "ZANDYLEE_ADM",
                "name" to "Andrew Lee",
                "email" to "andrew.lee@digital.justice.gov.uk",
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
                "userId" to "ZANDYLEE_ADM",
                "name" to "Andrew Lee",
                "email" to "andrew.lee@digital.justice.gov.uk",
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

        val renderedStyleTemplate = templateRenderService.renderTemplate("use-of-force", testServiceData)

        renderedStyleTemplate.shouldNotBeNull()
        renderedStyleTemplate.shouldContain("<style>")
        renderedStyleTemplate.shouldContain("</style>")
        renderedStyleTemplate.shouldContain("<td>Incident date</td><td>07 September 2020, 2:02:00 am</td>")
        renderedStyleTemplate.shouldContain("<td>CCTV recording</td><td>YES</td>")
        renderedStyleTemplate.shouldContain("<td>Name</td><td>Andrew Lee</td>")
        renderedStyleTemplate.shouldContain("<td>Baton drawn</td><td>false</td>")
        renderedStyleTemplate.shouldContain("<td>Staff ID</td><td>486084</td>")
      }
    }
  },
)
