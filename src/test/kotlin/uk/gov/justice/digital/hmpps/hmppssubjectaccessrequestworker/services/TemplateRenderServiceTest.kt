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
