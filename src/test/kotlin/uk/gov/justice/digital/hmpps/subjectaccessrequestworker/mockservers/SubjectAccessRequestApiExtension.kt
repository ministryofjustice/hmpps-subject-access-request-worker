package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestInstancePostProcessor
import org.slf4j.LoggerFactory

class SubjectAccessRequestApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  TestInstancePostProcessor {

  companion object {
    @JvmField
    val subjectAccessRequestApiMock = SubjectAccessRequestApiMockServer()
    val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun beforeAll(p0: ExtensionContext?) {
    subjectAccessRequestApiMock.start()
    log.info("started subjectAccessRequestApiMock: ${subjectAccessRequestApiMock.port()}")
  }

  override fun afterAll(p0: ExtensionContext?) {
    log.info("stopping subjectAccessRequestApiMock: ${subjectAccessRequestApiMock.port()}")
    subjectAccessRequestApiMock.stop()
  }

  override fun beforeEach(p0: ExtensionContext?) {
    log.info("resetting subjectAccessRequestApiMock: ${subjectAccessRequestApiMock.port()}")
    subjectAccessRequestApiMock.resetRequests()
  }

  override fun postProcessTestInstance(testInstance: Any?, context: ExtensionContext?) {
    try {
      val field = testInstance?.javaClass?.getField("sarApiMockServer")
      field?.set(testInstance, subjectAccessRequestApiMock)
    } catch (e: NoSuchFieldException) {
      log.error(e.message)
    }
  }
}

class SubjectAccessRequestApiMockServer : WireMockServer(
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
}
