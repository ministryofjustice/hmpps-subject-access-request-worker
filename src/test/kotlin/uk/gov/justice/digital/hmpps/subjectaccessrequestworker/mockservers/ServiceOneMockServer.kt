package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor

class ServiceOneMockServer : WireMockServer(4100) {

  companion object {
    const val SAR_RESPONSE = """
              {
                "content": {
                  "Service One Property": {
                    "field1": "value1"
                  }
                }
              }
    """
  }

  fun verifyNeverCalled() {
    verify(0, anyRequestedFor(anyUrl()))
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

  fun stubResponseFor(response: ResponseDefinitionBuilder, params: GetSubjectAccessRequestParams) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(response),
    )
  }

  fun stubSubjectAccessRequestErrorResponse(status: Int, params: GetSubjectAccessRequestParams) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(SAR_RESPONSE),
        ),
    )
  }

  fun stubSubjectAccessRequestErrorWith5xxOnInitialRequestSucceedOnRetry(params: GetSubjectAccessRequestParams) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .inScenario("fail-on-initial-request")
        .willReturn(
          aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json"),
        ).willSetStateTo("failed-initial-request"),
    )

    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .inScenario("fail-on-initial-request")
        .whenScenarioStateIs("failed-initial-request")
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

  fun stubSubjectAccessRequestSuccessNoBody(params: GetSubjectAccessRequestParams) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubSubjectAccessRequestFault(params: GetSubjectAccessRequestParams) {
    stubFor(
      get(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .willReturn(
          aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER),
        ),
    )
  }

  fun verifyGetSubjectAccessRequestSuccessIsCalled(times: Int, params: GetSubjectAccessRequestParams) {
    verify(
      times,
      getRequestedFor(urlPathEqualTo("/subject-access-request"))
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString())),
    )
  }

  fun verifyApiCalled(times: Int) = verify(
    times,
    getRequestedFor(urlPathEqualTo("/subject-access-request")),
  )
}

class ServiceOneApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  TestInstancePostProcessor {

  companion object {
    @JvmField
    val serviceOneMockApi = ServiceOneMockServer()
  }

  override fun beforeAll(p0: ExtensionContext?) {
    serviceOneMockApi.start()
  }

  override fun afterAll(p0: ExtensionContext?) {
    serviceOneMockApi.stop()
  }

  override fun beforeEach(p0: ExtensionContext?) {
    serviceOneMockApi.resetRequests()
    serviceOneMockApi.resetScenarios()
  }

  override fun postProcessTestInstance(testInstance: Any?, context: ExtensionContext?) {
    try {
      val field = testInstance?.javaClass?.getField("complexityOfNeedsMockApi")
      field?.set(testInstance, serviceOneMockApi)
    } catch (e: NoSuchFieldException) {
    }
  }
}
