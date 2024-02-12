package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import org.springframework.http.HttpStatus

class ComplexityOfNeedMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 4000
  }

  private val sarEndpoint = "/subject-access-request"

  fun stubGetSubjectAccessRequestData(
    prn: String,
    fromDate: String,
    toDate: String,
    responseBody: String,
    status: HttpStatus = HttpStatus.OK,
  ) {
    stubFor(
      get(sarEndpoint)
        .withHeader("Authorization", matching("Bearer ${HmppsAuthMockServer.TOKEN}"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(responseBody.trimIndent()),
        ),
    )
  }
}
