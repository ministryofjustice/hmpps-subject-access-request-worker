package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.google.gson.Gson
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient

class ProbationApiMockServer : WireMockServer(4002) {

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
      get("/probation-case/$subjectId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "name" : {
                  "forename": "eric",
                  "surname": "Wimp"
                  }
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubGetOffenderDetails(subjectId: String, apiResponse: ProbationApiClient.GetOffenderDetailsResponse) {
    stubFor(
      get("/probation-case/$subjectId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(Gson().toJson(apiResponse)),
        ),
    )
  }

  fun stubResponseFor(subjectId: String, responseDefinitionBuilder: ResponseDefinitionBuilder) {
    stubFor(
      get("/probation-case/$subjectId")
        .willReturn(responseDefinitionBuilder),
    )
  }

  fun verifyGetOffenderDetailsCalled(times: Int = 1, subjectId: String) {
    verify(times, getRequestedFor(urlPathEqualTo("/probation-case/$subjectId")))
  }
}

class ProbationApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val probationApi = ProbationApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = probationApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = probationApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = probationApi.stop()
}
