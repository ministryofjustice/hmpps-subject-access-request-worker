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
import kotlin.text.trimIndent

class LocationsMockServer : WireMockServer(8085) {

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

  fun stubGetLocation(dpsLocationId: String) {
    stubFor(
      get("/locations/$dpsLocationId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """{
                  "id": "$dpsLocationId",
                  "localName": "PROPERTY BOX 27",
                  "pathHierarchy": "PROP_BOXES-PB027"
                }"""
                .trimIndent(),
            ),
        ),
    )
  }

  fun stubGetLocationResponse(dpsLocationId: String, response: ResponseDefinitionBuilder) {
    stubFor(
      get("/locations/$dpsLocationId")
        .willReturn(response),
    )
  }

  fun verifyGetLocationNeverCalled(dpsLocationId: String) = verify(
    0,
    getRequestedFor(urlPathEqualTo("/locations/$dpsLocationId")),
  )

  fun verifyGetLocationCalled(times: Int, dpsLocationId: String) = verify(
    times,
    getRequestedFor(urlPathEqualTo("/locations/$dpsLocationId")),
  )
}

class LocationsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val locationsApi = LocationsMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = locationsApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = locationsApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = locationsApi.stop()
}
