package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import java.util.UUID

class SubjectAccessRequestApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  TestInstancePostProcessor {

  companion object {
    @JvmField
    val subjectAccessRequestApiMock = SubjectAccessRequestApiMockServer()
  }

  override fun beforeAll(p0: ExtensionContext?) {
    subjectAccessRequestApiMock.start()
  }

  override fun afterAll(p0: ExtensionContext?) {
    subjectAccessRequestApiMock.stop()
  }

  override fun beforeEach(p0: ExtensionContext?) {
    subjectAccessRequestApiMock.resetRequests()
    subjectAccessRequestApiMock.resetScenarios()
  }

  override fun postProcessTestInstance(testInstance: Any?, context: ExtensionContext?) {
    try {
      val field = testInstance?.javaClass?.getField("sarApiMockServer")
      field?.set(testInstance, subjectAccessRequestApiMock)
    } catch (e: NoSuchFieldException) { }
  }
}

class SubjectAccessRequestApiMockServer :
  WireMockServer(
    WireMockConfiguration.wireMockConfig().port(4000),
  ) {

  companion object {
    const val UNCLAIMED_SAR_BODY = """
      [
        {
            "id": "72b5b7c3-a6e9-4b31-b0ba-5a00484714b6",
            "status": "Pending",
            "dateFrom": null,
            "dateTo": "2024-10-09",
            "sarCaseReferenceNumber": "TEST_CASE_001",
            "services": "hmpps-complexity-of-need, https://localhost:8080/some-name-here",
            "nomisId": "666999",
            "ndeliusCaseReferenceId": null,
            "requestedBy": "BOB",
            "requestDateTime": "2024-10-09T15:51:16.729913",
            "claimDateTime": null,
            "claimAttempts": 0,
            "objectUrl": null,
            "lastDownloaded": null
        }
      ]
    """

    const val GENERIC_ERROR_BODY = """
      {
        "error": "some error happened"
      }
    """
  }

  fun stubGetUnclaimedRequestsSuccess(token: String) {
    stubFor(
      get("/api/subjectAccessRequests?unclaimed=true")
        .withHeader("Authorization", matching("Bearer $token"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(UNCLAIMED_SAR_BODY),
        ),
    )
  }

  fun stubGetUnclaimedRequestsFailsOnFirstAttemptWithStatus(status: Int, token: String) {
    stubFor(
      get("/api/subjectAccessRequests?unclaimed=true")
        .withHeader("Authorization", matching("Bearer $token"))
        .inScenario("fails on first attempt")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status)
            .withBody(GENERIC_ERROR_BODY),
        ).willSetStateTo("first-request-fails-with-500"),
    )

    stubFor(
      get("/api/subjectAccessRequests?unclaimed=true")
        .withHeader("Authorization", matching("Bearer $token"))
        .inScenario("fails on first attempt")
        .whenScenarioStateIs("first-request-fails-with-500")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(UNCLAIMED_SAR_BODY),
        ),
    )
  }

  fun stubGetUnclaimedRequestsFailsWith500ThenFailsWith401ThenSucceeds(token: String) {
    stubFor(
      get("/api/subjectAccessRequests?unclaimed=true")
        .withHeader("Authorization", matching("Bearer $token"))
        .inScenario("fails on first attempt")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(500)
            .withBody(GENERIC_ERROR_BODY),
        ).willSetStateTo("first-request-fails-with-500"),
    )

    stubFor(
      get("/api/subjectAccessRequests?unclaimed=true")
        .withHeader("Authorization", matching("Bearer $token"))
        .inScenario("fails on first attempt")
        .whenScenarioStateIs("first-request-fails-with-500")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(401)
            .withBody(GENERIC_ERROR_BODY),
        ).willSetStateTo("second-request-fails-with-401"),
    )

    stubFor(
      get("/api/subjectAccessRequests?unclaimed=true")
        .withHeader("Authorization", matching("Bearer $token"))
        .inScenario("fails on first attempt")
        .whenScenarioStateIs("second-request-fails-with-401")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(UNCLAIMED_SAR_BODY),
        ),
    )
  }

  fun stubGetUnclaimedRequestsFailsWithStatus(status: Int, token: String) {
    stubFor(
      get("/api/subjectAccessRequests?unclaimed=true")
        .withHeader("Authorization", matching("Bearer $token"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status)
            .withBody(GENERIC_ERROR_BODY),
        ),
    )
  }

  fun stubClaimSARReturnsStatus(status: Int, sarId: UUID, token: String) {
    stubFor(
      patch("/api/subjectAccessRequests/$sarId/claim")
        .withHeader("Authorization", matching("Bearer $token"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status),
        ),
    )
  }

  fun stubClaimSARErrorsWith5xxOnInitialRequestAndReturnsStatusOnRetry(
    retryResponseStatus: Int,
    sarId: UUID,
    token: String,
  ) {
    stubFor(
      patch("/api/subjectAccessRequests/$sarId/claim")
        .withHeader("Authorization", matching("Bearer $token"))
        .inScenario("fails on first attempt")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(500),
        ).willSetStateTo("failed-first-request"),
    )

    stubFor(
      patch("/api/subjectAccessRequests/$sarId/claim")
        .withHeader("Authorization", matching("Bearer $token"))
        .inScenario("fails on first attempt")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(retryResponseStatus),
        ).whenScenarioStateIs("failed-first-request"),
    )
  }

  fun stubCompleteSubjectAccessRequest(responseStatus: Int, sarId: UUID, token: String) {
    stubFor(
      patch("/api/subjectAccessRequests/$sarId/complete")
        .withHeader("Authorization", matching("Bearer $token"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(responseStatus),
        ),
    )
  }

  fun stubCompleteSubjectAccessRequestFailsWith5xxOnFirstRequestSucceedsOnRetry(sarId: UUID, token: String) {
    stubFor(
      patch("/api/subjectAccessRequests/$sarId/complete")
        .withHeader("Authorization", matching("Bearer $token"))
        .inScenario("fail-on-first-request")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(500),
        ).willSetStateTo("failed-first-request"),
    )

    stubFor(
      patch("/api/subjectAccessRequests/$sarId/complete")
        .withHeader("Authorization", matching("Bearer $token"))
        .inScenario("fail-on-first-request")
        .whenScenarioStateIs("failed-first-request")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun verifyGetUnclaimedSubjectAccessRequestsIsCalled(times: Int, token: String) {
    verify(
      times,
      getRequestedFor(urlPathEqualTo("/api/subjectAccessRequests"))
        .withHeader("Authorization", equalTo("Bearer $token")),
    )
  }

  fun verifyClaimSubjectAccessRequestIsCalled(times: Int, sarId: UUID, token: String) {
    verify(
      times,
      patchRequestedFor(urlPathEqualTo("/api/subjectAccessRequests/$sarId/claim"))
        .withHeader("Authorization", equalTo("Bearer $token")),
    )
  }

  fun verifyCompleteSubjectAccessRequestIsCalled(times: Int, sarId: UUID, token: String) {
    verify(
      times,
      patchRequestedFor(urlPathEqualTo("/api/subjectAccessRequests/$sarId/complete"))
        .withHeader("Authorization", equalTo("Bearer $token")),
    )
  }

  fun verifyZeroInteractions() {
    verify(0, anyRequestedFor(anyUrl()))
  }
}
