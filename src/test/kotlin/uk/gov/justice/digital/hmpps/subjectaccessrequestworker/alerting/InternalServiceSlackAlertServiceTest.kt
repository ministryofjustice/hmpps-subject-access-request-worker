package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Sender behaviour is tested in hmpps-subject-access-request-lib; this only verifies the adapter wiring.
 */
class InternalServiceSlackAlertServiceTest {
  private val slack = WireMockServer(options().dynamicPort())

  @BeforeEach
  fun start() {
    slack.start()
  }

  @AfterEach
  fun stop() {
    slack.stop()
  }

  @Test
  fun `should pass configured values through to the shared Slack alert sender`() {
    val requestId = UUID.randomUUID()
    slack.stubFor(post(urlEqualTo("/chat.postMessage")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""{"ok":true}""")))

    InternalServiceSlackAlertService(slack.baseUrl(), "test-token", listOf("HAAR_CHANNEL"), "sar-worker", "test")
      .sendServiceCallFailedAlert("SAR HTML renderer", "render", requestId, "test-service")

    slack.verify(
      1,
      postRequestedFor(urlEqualTo("/chat.postMessage"))
        .withHeader("Authorization", equalTo("Bearer test-token"))
        .withRequestBody(matchingJsonPath("$.channel", equalTo("HAAR_CHANNEL")))
        .withRequestBody(
          matchingJsonPath(
            "$.text",
            equalTo(
              "Subject Access Request: Internal Service Call Failed\nEnvironment: test\nSource: sar-worker\n" +
                "Destination: SAR HTML renderer\nOperation: render\nSAR ID: $requestId\nService: test-service\n" +
                "Please investigate the service logs.",
            ),
          ),
        ),
    )
  }
}
