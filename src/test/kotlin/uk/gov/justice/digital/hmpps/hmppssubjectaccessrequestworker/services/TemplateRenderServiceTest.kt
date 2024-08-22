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
          arrayListOf(mapOf(
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
            "paragraphDescription" to "Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:"
            ),
            "associatedPrisonersNumber" to "A3864DZ",
            ),
            "offenceDetails" to mapOf(
            "offenceCode" to 16001,
            "offenceRule" to mapOf(
            "paragraphNumber" to "16",
            "paragraphDescription" to "Intentionally or recklessly sets fire to any part of a prison or any other property, whether or not her own",
            "nomisCode" to "51:16",
            "withOthersNomisCode" to "51:25C"
            ),
            "protectedCharacteristics" to mapOf(
              "id" to 247,
              "characteristic" to "AGE",
            )
          ),
            "incidentStatement" to mapOf(
            "statement" to "Vera incited Brian Duckworth to set fire to a lamp\r\ndamages - the lamp\r\nevidence includes something in a bag with a reference number of 1234\r\nwitnessed by amarktest",
            "completed" to true
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
              "reporter" to "LBENNETT_GEN"
              )
            ),
            "evidence" to arrayListOf(
              mapOf(
              "code" to "BAGGED_AND_TAGGED",
              "identifier" to "1234",
              "details" to "evidence in a bag with a reference number",
              "reporter" to "LBENNETT_GEN")
            ),
            "witnesses" to arrayListOf(
              mapOf(
              "code" to "OFFICER",
              "firstName" to "Andrew",
              "lastName" to "Marke",
              "reporter" to "LBENNETT_GEN"
              )
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
                  "plea" to "GUILTY"
                  ),
                "agencyId" to "MDI"
              )
            ),
            "disIssueHistory" to arrayListOf(
              mapOf("issuingOfficer" to "someone",
                "dateTimeOfIssue" to "2023-06-08T14:25:00",)
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
                  )
                )
              )
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
                  "endDate" to "2023-06-16"
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
                  "measurement" to "DAYS"
                ),
                "damagesOwedAmount" to 20,
                "canRemove" to true,
                "canEdit" to true,
                "rehabilitativeActivities" to emptyList<Any>()
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
        val renderedStyleTemplate = templateRenderService.renderTemplate("hmpps-manage-adjudications-api", testServiceData)
        renderedStyleTemplate.shouldNotBeNull()
        renderedStyleTemplate.shouldContain("<style>")
        renderedStyleTemplate.shouldContain("</style>")
        renderedStyleTemplate.shouldContain("<td>Prisoner number</td><td>A3863DZ</td>")
        renderedStyleTemplate.shouldContain("<td>Date and time of incident</td><td>08 June 2023, 12:00:00 pm</td>")
        renderedStyleTemplate.shouldContain("<td>Incident role code</td><td>25c</td>")
        renderedStyleTemplate.shouldContain("<td>Description</td><td>Assists another prisoner to commit, or to attempt to commit, any of the foregoing offences:</td>")
        renderedStyleTemplate.shouldContain("<td>Description</td><td>Intentionally or recklessly sets fire to any part of a prison or any other property, whether or not her own</td>")
        renderedStyleTemplate.shouldContain("<td>Is completed</td><td>true</td>")
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
  },
)
