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

    describe("complexityOfNeedTemplate") {
      it("renders a template given a complexity of need template") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "offenderNo" to "A1234AA",
            "level" to "low",
            "sourceSystem" to "keyworker-to-complexity-api-test",
            "sourceUser" to "JSMITH_GEN",
            "notes" to "string",
            "createdTimeStamp" to "2021-03-30T11:45:10.266Z",
            "active" to true,
          ),
          mapOf(
            "offenderNo" to "A1234AA",
            "level" to "low",
            "sourceSystem" to "keyworker-to-complexity-api-test",
            "sourceUser" to "JSMITH_GEN",
            "notes" to "string",
            "createdTimeStamp" to "2021-03-30T19:54:46.056Z",
            "active" to true,
          ),
        )
        val renderedStyleTemplate = templateRenderService.renderTemplate("complexity-of-need", testServiceData)
        renderedStyleTemplate.shouldNotBeNull()
        renderedStyleTemplate.shouldContain("<style>")
        renderedStyleTemplate.shouldContain("</style>")
        renderedStyleTemplate.shouldContain("<td>Offender number</td><td>A1234AA</td>")
        renderedStyleTemplate.shouldContain("<td>Source system</td><td>keyworker-to-complexity-api-test</td>")
      }
    }
  },
)
