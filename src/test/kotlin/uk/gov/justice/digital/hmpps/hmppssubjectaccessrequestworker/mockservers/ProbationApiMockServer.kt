package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching

class ProbationApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 4002
  }

  private val offenderIdPath = "/probation-case/A999999"

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
                "name" : {
                  "forename": "FirstName",
                  "surname": "LastName"
                  }
              }
              """.trimIndent(),
            ),
        ),
    )
  }
}
