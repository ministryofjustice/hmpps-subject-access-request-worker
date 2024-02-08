package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.mockservers.HmppsAuthMockServer

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

      hmppsAuthMockServer.stubGetOAuthToken("username", "password")
    }

    afterTest {
      hmppsAuthMockServer.stop()
    }

    it("throws an exception if connection is refused") {
      hmppsAuthMockServer.stop()

      val exception = shouldThrow<RuntimeException> {
        hmppsAuthGateway.getClientToken()
      }

      exception.message.shouldBe("Connection to localhost:3000 failed.")
    }

  })
