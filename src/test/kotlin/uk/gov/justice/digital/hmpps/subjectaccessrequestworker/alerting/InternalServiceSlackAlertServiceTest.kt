package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension::class)
class InternalServiceSlackAlertServiceTest {
  private val slack = WireMockServer(options().dynamicPort())
  private val requestId = UUID.randomUUID()

  @BeforeAll
  fun start() {
    slack.start()
  }

  @AfterAll
  fun stop() {
    slack.stop()
  }

  @BeforeEach
  fun reset() {
    slack.resetAll()
  }

  private fun service(channels: List<String> = listOf("HAAR_CHANNEL", " HAAR_CHANNEL ")) = InternalServiceSlackAlertService(
    slack.baseUrl(),
    "test-token",
    channels,
    "sar-worker",
    "test",
  )

  @Test
  fun `should send plain text alert only to configured HAAR channels without duplicates`(output: CapturedOutput) {
    slack.stubFor(post(urlEqualTo("/chat.postMessage")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""{"ok":true}""")))

    service().sendServiceCallFailedAlert("SAR HTML renderer", "render", requestId, "test-service")

    slack.verify(
      1,
      postRequestedFor(urlEqualTo("/chat.postMessage"))
        .withHeader("Authorization", equalTo("Bearer test-token"))
        .withRequestBody(matchingJsonPath("$.channel", equalTo("HAAR_CHANNEL")))
        .withRequestBody(matchingJsonPath("$.mrkdwn", equalTo("false")))
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
    assertThat(output.all).doesNotContain("Failed to send internal service call Slack alert")
  }

  @Test
  fun `should log Slack rejection and continue to other HAAR channels`(output: CapturedOutput) {
    slack.stubFor(post(urlEqualTo("/chat.postMessage")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""{"ok":false,"error":"channel_not_found"}""")))

    assertDoesNotThrow {
      service(listOf("HAAR_ONE", "HAAR_TWO")).sendServiceCallFailedAlert("SAR HTML renderer", "render", requestId, "test-service")
    }

    slack.verify(2, postRequestedFor(urlEqualTo("/chat.postMessage")))
    assertThat(output.all).contains("channel_not_found")
  }

  @Test
  fun `should not propagate Slack HTTP errors or prevent other recipients being attempted`() {
    slack.stubFor(post(urlEqualTo("/chat.postMessage")).willReturn(aResponse().withStatus(500)))

    assertDoesNotThrow {
      service(listOf("HAAR_ONE", "HAAR_TWO")).sendServiceCallFailedAlert("SAR HTML renderer", "render", requestId, "test-service")
    }

    slack.verify(2, postRequestedFor(urlEqualTo("/chat.postMessage")))
  }

  @Test
  fun `should reject missing HAAR channel configuration`() {
    assertThrows<IllegalArgumentException> { service(listOf(" ")) }
  }
}
