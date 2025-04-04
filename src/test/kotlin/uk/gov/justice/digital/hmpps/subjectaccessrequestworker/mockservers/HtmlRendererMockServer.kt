package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient

class HtmlRendererMockServer : WireMockServer(8087) {

  private val objectMapper = ObjectMapper().registerModules(kotlinModule(), JavaTimeModule())

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"status":"${if (status == 200) "UP" else "DOWN"}"}""")
          .withStatus(status),
      ),
    )
  }

  fun successResponse(documentKey: String): ResponseDefinitionBuilder = ResponseDefinitionBuilder.responseDefinition()
    .withStatus(201)
    .withHeader("Content-Type", "application/json")
    .withBody("""{ "documentKey": "$documentKey" }""".trimIndent())

  fun errorResponse(status: HttpStatus): ResponseDefinitionBuilder = ResponseDefinitionBuilder.responseDefinition()
    .withStatus(status.value())
    .withHeader("Content-Type", "application/json")
    .withBody(status.reasonPhrase)

  fun stubRenderHtmlResponses(
    renderRequest: HtmlRendererApiClient.HtmlRenderRequest,
    responseOne: ResponseDefinitionBuilder,
    responseTwo: ResponseDefinitionBuilder,
  ) {
    stubFor(
      post("/subject-access-request/render")
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(renderRequest)))
        .inScenario("response-1-then-2")
        .willReturn(responseOne)
        .willSetStateTo("response-1-done"),
    )

    stubFor(
      post("/subject-access-request/render")
        .inScenario("response-1-then-2")
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(renderRequest)))
        .whenScenarioStateIs("response-1-done")
        .willReturn(responseTwo),
    )
  }

  fun stubRenderResponsesWith(
    renderRequest: HtmlRendererApiClient.HtmlRenderRequest,
    responseDefinition: ResponseDefinitionBuilder,
  ) {
    stubFor(
      post("/subject-access-request/render")
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(renderRequest)))
        .willReturn(responseDefinition),
    )
  }

  fun verifyRenderCalled(times: Int, expectedBody: HtmlRendererApiClient.HtmlRenderRequest) {
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/subject-access-request/render"))
        .withRequestBody(equalToJson(objectMapper.writeValueAsString(expectedBody))),
    )
  }

  fun verifyRenderNeverCalled() = verify(0, anyRequestedFor(anyUrl()))
}

class HtmlRendererApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val htmlRendererApi = HtmlRendererMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = htmlRendererApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = htmlRendererApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = htmlRendererApi.stop()
}
