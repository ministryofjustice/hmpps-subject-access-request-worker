package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class NomisMappingsMockServer : WireMockServer(8086) {

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

  fun stubLocationMapping(nomisId: Int) {
    stubFor(
      get("/api/locations/nomis/$nomisId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """{
                  "dpsLocationId": "000047c0-38e1-482e-8bbc-07d4b5f57e23",
                  "nomisLocationId": $nomisId
                }"""
                .trimIndent(),
            ),
        ),
    )
  }

  fun stubGetLocationMappingResponse(nomisId: Int, response: ResponseDefinitionBuilder) {
    stubFor(
      get("/api/locations/nomis/$nomisId")
        .willReturn(response),
    )
  }

  fun verifyGetLocationMappingNeverCalled(nomisId: Int) = verify(
    0,
    getRequestedFor(urlPathEqualTo("/api/locations/nomis/$nomisId")),
  )

  fun verifyGetLocationMappingCalled(times: Int, nomisId: Int) = verify(
    times,
    getRequestedFor(urlPathEqualTo("/api/locations/nomis/$nomisId")),
  )
}

class NomisMappingsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val nomisMappingsApi = NomisMappingsMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = nomisMappingsApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = nomisMappingsApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = nomisMappingsApi.stop()
}
