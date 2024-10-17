package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ComplexityOfNeedsApiExtension.Companion.complexityOfNeedsMockApi
import java.time.LocalDate

class ComplexityOfNeedsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  TestInstancePostProcessor {

  companion object {
    @JvmField
    val complexityOfNeedsMockApi = ComplexityOfNeedsMockServer()
  }

  override fun beforeAll(p0: ExtensionContext?) {
    complexityOfNeedsMockApi.start()
  }

  override fun afterAll(p0: ExtensionContext?) {
    complexityOfNeedsMockApi.stop()
  }

  override fun beforeEach(p0: ExtensionContext?) {
    complexityOfNeedsMockApi.resetRequests()
    complexityOfNeedsMockApi.resetScenarios()
  }

  override fun postProcessTestInstance(testInstance: Any?, context: ExtensionContext?) {
    try {
      val field = testInstance?.javaClass?.getField("complexityOfNeedsMockApi")
      field?.set(testInstance, complexityOfNeedsMockApi)
    } catch (e: NoSuchFieldException) {
    }
  }
}

class ComplexityOfNeedsMockServer : WireMockServer(
  WireMockConfiguration.wireMockConfig().port(4100),
) {

  fun verifyZeroInteractions() {
    verify(0, anyRequestedFor(anyUrl()))
  }

  fun stubSubjectAccessRequestSuccessResponse(params: GetSubjectAccessRequestParams) {
    complexityOfNeedsMockApi.stubFor(
      get(
        urlPathEqualTo("/subject-access-request"),
      )
        .withQueryParam("prn", equalTo(params.prn))
        .withQueryParam("crn", equalTo(params.crn))
        .withQueryParam("fromDate", equalTo(params.dateFrom.toString()))
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .withHeader("Authorization", equalTo("Bearer ${params.authToken}"))
        .willReturn(
          aResponse()
            .withStatus(200).withHeader("Content-Type", "application/json")
            .withBody(
              """
              {
                "content": {
                  "additionalProp1": {
                    "field1": "value1"
                  }
                }
              }
              """.trimIndent(),
            ),
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
        .withQueryParam("toDate", equalTo(params.dateTo.toString()))
        .withHeader("Authorization", equalTo("Bearer ${params.authToken}")),
    )
  }

  data class GetSubjectAccessRequestParams(
    val prn: String? = null,
    val crn: String? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val authToken: String,
  )
}
