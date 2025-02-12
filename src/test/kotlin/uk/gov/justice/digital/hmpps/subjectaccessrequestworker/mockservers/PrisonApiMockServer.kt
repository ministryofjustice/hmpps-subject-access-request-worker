package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.google.gson.Gson
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient

class PrisonApiMockServer : WireMockServer(8079) {

  fun verifyNeverCalled() {
    verify(0, anyRequestedFor(anyUrl()))
  }

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

  fun stubGetOffenderDetails(subjectId: String) {
    stubFor(
      get("/api/offenders/$subjectId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "firstName": "JOE",
                "middleName": "Jack",
                "lastName": "Reacher"
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubGetOffenderDetails(subjectId: String, responseBody: PrisonApiClient.GetOffenderDetailsResponse) {
    stubFor(
      get("/api/offenders/$subjectId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(Gson().toJson(responseBody)),
        ),
    )
  }

  fun stubResponseFor(subjectId: String, response: ResponseDefinitionBuilder) {
    stubFor(
      get("/api/offenders/$subjectId")
        .willReturn(response),
    )
  }

  fun verifyApiNeverCalled() = verify(
    0,
    anyRequestedFor(anyUrl()),
  )

  fun verifyApiCalled(times: Int, subjectId: String) = verify(
    times,
    getRequestedFor(urlPathEqualTo("/api/offenders/$subjectId")),
  )
}

class PrisonApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val prisonApi = PrisonApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = prisonApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = prisonApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = prisonApi.stop()
}
