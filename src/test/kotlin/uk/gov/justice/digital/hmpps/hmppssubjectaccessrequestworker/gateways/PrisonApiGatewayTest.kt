package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.config.ApplicationInsightsConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.mockservers.PrisonApiMockServer

@ActiveProfiles("test")
@Import(ApplicationInsightsConfiguration::class)
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(GenericHmppsApiGateway::class)],
)
class PrisonApiGatewayTest(
  prisonApiGateway: PrisonApiGateway,
  @MockBean val mockHmppsAuthGateway: HmppsAuthGateway,
) : DescribeSpec(
  {
    val prisonApiMockServer = PrisonApiMockServer()

    Mockito.`when`(mockHmppsAuthGateway.getClientToken()).thenReturn("mock-bearer-token")

    beforeEach {
      prisonApiMockServer.start()
      prisonApiMockServer.stubGetOffenderDetails()
    }

    afterTest {
      prisonApiMockServer.stop()
    }

    describe("getOffenderName") {
      it("returns the offender name") {
        val offenderName = prisonApiGateway.getOffenderName("A9999AA")
        offenderName.shouldBe("FIRSTNAME MIDDLENAME LASTNAME")
      }
    }
  },
)
