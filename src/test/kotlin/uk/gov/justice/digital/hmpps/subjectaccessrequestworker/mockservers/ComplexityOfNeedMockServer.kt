package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching

class ComplexityOfNeedMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 4000
  }

  private val sarEndpoint = "/subject-access-request?prn=validPrn&fromDate&toDate"

  fun stubGetSubjectAccessRequestData() {
    stubFor(
      get(sarEndpoint)
        .withHeader("Authorization", matching("Bearer ${HmppsAuthMockServer.TOKEN}"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "content": {
                  "additionalProp1": {}
                }
              }
              """.trimIndent(),
            ),
        ),
    )
  }
}
