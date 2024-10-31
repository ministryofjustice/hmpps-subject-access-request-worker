package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

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

  fun stubGetOffenderDetails() {
    stubFor(
      get("/probation-case/A999999")
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
}

class ProbationApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val probationApi = ProbationApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext): Unit = probationApi.start()
  override fun beforeEach(context: ExtensionContext): Unit = probationApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = probationApi.stop()
}
