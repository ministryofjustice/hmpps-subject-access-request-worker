package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching

class PrisonApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 4001
  }

  private val offenderIdPath = "/api/offenders/A9999AA"

  fun stubGetOffenderDetails() {
    stubFor(
      get(offenderIdPath)
        .withHeader("Authorization", matching("Bearer ${HmppsAuthMockServer.TOKEN}"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "firstName": "FIRSTNAME",
                "middleName": "MIDDLENAME",
                "lastName": "LASTNAME"
              }
              """.trimIndent(),
            ),
        ),
    )
  }
}
