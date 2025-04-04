package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.time.LocalDateTime
import java.time.ZoneOffset

class HmppsAuthApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmppsAuth = HmppsAuthMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmppsAuth.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmppsAuth.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    hmppsAuth.stop()
  }
}

class HmppsAuthMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 9090
  }

  fun verifyNeverCalled() {
    verify(0, anyRequestedFor(anyUrl()))
  }

  fun verifyCalledOnce() {
    verify(1, anyRequestedFor(anyUrl()))
  }

  fun stubGrantTokenResponse(responseDefinitionBuilder: ResponseDefinitionBuilder) {
    stubFor(
      post(urlEqualTo("/auth/oauth/token"))
        .willReturn(responseDefinitionBuilder),
    )
  }

  fun stubGrantToken() {
    stubFor(
      post(urlEqualTo("/auth/oauth/token"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              """
                {
                  "token_type": "bearer",
                  "access_token": "ABCDE",
                  "expires_in": ${LocalDateTime.now().plusMinutes(5).toEpochSecond(ZoneOffset.UTC)}
                }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubGrantToken(responseDefinitionBuilder: ResponseDefinitionBuilder) {
    stubFor(
      post(urlEqualTo("/auth/oauth/token"))
        .willReturn(responseDefinitionBuilder),
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/auth/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) """{"status":"UP"}""" else """{"status":"DOWN"}""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetOAuthToken(client: String, clientSecret: String) {
    stubFor(
      post("/auth/oauth/token?grant_type=client_credentials")
        .withBasicAuth(client, clientSecret)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
                {
                  "token_type": "bearer",
                  "access_token": "ABCDE",
                  "expires_in": ${LocalDateTime.now().plusHours(2).toEpochSecond(ZoneOffset.UTC)}
                }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubServiceUnavailableForGetOAuthToken() {
    stubFor(
      post("/auth/oauth/token?grant_type=client_credentials")
        .willReturn(
          WireMock.serviceUnavailable(),
        ),
    )
  }

  fun stubUnauthorizedForGetOAAuthToken() {
    stubFor(
      post("/auth/oauth/token?grant_type=client_credentials")
        .willReturn(
          WireMock.unauthorized(),
        ),
    )
  }
}
