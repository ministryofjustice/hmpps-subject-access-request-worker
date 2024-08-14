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
  },
)
