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
        renderedStyleTemplate.shouldContain("<td>Offender Keyworker ID</td><td>12912</td>")
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
        renderedStyleTemplate.shouldContain("<td>End date</td><td>21 July 2023</td>")
        renderedStyleTemplate.shouldContain("<td>Count</td><td>8</td>")
        renderedStyleTemplate.shouldContain("<td>Count</td><td>12</td>")
        renderedStyleTemplate.shouldContain("<td>Waiting list ID</td><td>1</td>")
        renderedStyleTemplate.shouldContain("<td>Waiting list ID</td><td>10</td>")
        renderedStyleTemplate.shouldContain("<td>Status date</td><td>12 November 2022</td>")
        renderedStyleTemplate.shouldContain("<td>Appointment ID</td><td>18305</td>")
        renderedStyleTemplate.shouldContain("<td>Appointment ID</td><td>16340</td>")
      }
    }

    describe("incentivesTemplate") {
      it("renders a template given a incentives template") {
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
  },
)
