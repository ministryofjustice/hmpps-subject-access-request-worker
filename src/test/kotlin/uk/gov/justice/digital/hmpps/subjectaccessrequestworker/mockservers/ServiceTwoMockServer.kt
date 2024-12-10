package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client.GetSubjectAccessRequestParams

class ServiceTwoMockServer : WireMockServer(4200) {

  companion object {
    const val SAR_RESPONSE = """
              {
                "content": {
                  "Service Two Property": {
                    "field1": "value1"
                  }
                }
              }
    """
  }

  fun stubSubjectAccessRequestSuccessResponse(params: GetSubjectAccessRequestParams) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(SAR_RESPONSE),
        ),
    )
  }
}

class ServiceTwoApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  TestInstancePostProcessor {

  companion object {
    @JvmField
    val serviceTwoMockApi = ServiceTwoMockServer()
  }

  override fun beforeAll(context: ExtensionContext?) {
    serviceTwoMockApi.start()
  }
  override fun afterAll(context: ExtensionContext?) {
    serviceTwoMockApi.stop()
  }
  override fun beforeEach(context: ExtensionContext?) {
    serviceTwoMockApi.resetAll()
  }

  override fun postProcessTestInstance(testInstance: Any?, context: ExtensionContext?) {
    try {
      val field = testInstance?.javaClass?.getField("complexityOfNeedsMockApi")
      field?.set(testInstance, serviceTwoMockApi)
    } catch (e: NoSuchFieldException) {
    }
  }
}
