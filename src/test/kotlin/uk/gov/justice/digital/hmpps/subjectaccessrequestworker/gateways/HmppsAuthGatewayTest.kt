package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthMockServer

@ActiveProfiles("test")
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(HmppsAuthGateway::class)],
)
class HmppsAuthGatewayTest(hmppsAuthGateway: HmppsAuthGateway) :
  DescribeSpec({
    val hmppsAuthMockServer = HmppsAuthMockServer()

    beforeEach {
      hmppsAuthMockServer.start()

      hmppsAuthMockServer.stubGetOAuthToken("mock-username", "mock-password")
    }

    afterTest {
      hmppsAuthMockServer.stop()
    }

    it("provides an auth token when called with valid client credentials") {

      val token = hmppsAuthGateway.getClientToken()

      token.shouldBe("mock-bearer-token")
    }

    it("throws an exception if connection is refused") {
      hmppsAuthMockServer.stop()

      val exception = shouldThrow<RuntimeException> {
        hmppsAuthGateway.getClientToken()
      }

      exception.message.shouldBe("Connection to localhost:3000 failed.")
    }

    it("throws an exception if auth service is unavailable") {
      hmppsAuthMockServer.stubServiceUnavailableForGetOAuthToken()

      val exception = shouldThrow<RuntimeException> {
        hmppsAuthGateway.getClientToken()
      }

      exception.message.shouldBe("localhost:3000 is unavailable.")
    }

    it("throws an exception if credentials are invalid") {
      hmppsAuthMockServer.stubUnauthorizedForGetOAAuthToken()

      val exception = shouldThrow<RuntimeException> {
        hmppsAuthGateway.getClientToken()
      }

      exception.message.shouldBe("Invalid credentials used.")
    }
  })
