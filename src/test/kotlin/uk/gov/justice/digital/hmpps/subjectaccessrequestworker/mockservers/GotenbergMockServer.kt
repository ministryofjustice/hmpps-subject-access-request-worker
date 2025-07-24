package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aMultipart
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class GotenbergMockServer : WireMockServer(3001) {

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"status":"${if (status == 200) "UP" else "DOWN"}"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubOfficeConvert(responseFileBytes: ByteArray) {
    stubFor(
      post("/forms/libreoffice/convert")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/pdf")
            .withStatus(200)
            .withBody(responseFileBytes),
        ),
    )
  }

  fun stubOfficeConvertResponse(response: ResponseDefinitionBuilder) {
    stubFor(
      post("/forms/libreoffice/convert")
        .willReturn(response),
    )
  }

  fun verifyOfficeConvertCalled(times: Int, filename: String) = verify(
    times,
    postRequestedFor(urlPathEqualTo("/forms/libreoffice/convert"))
      .withRequestBodyPart(
        aMultipart()
          .withName("files")
          .withHeader("Content-Disposition", containing("filename=$filename"))
          .build(),
      ),
  )
}

class GotenbergApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val gotenbergApi = GotenbergMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    gotenbergApi.start()
    System.setProperty("gotenberg-api.url", "http://localhost:${gotenbergApi.port()}")
  }
  override fun beforeEach(context: ExtensionContext): Unit = gotenbergApi.resetAll()
  override fun afterAll(context: ExtensionContext): Unit = gotenbergApi.stop()
}
