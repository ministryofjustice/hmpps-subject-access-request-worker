package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.ApplicationInsightsConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ProbationApiMockServer

@ActiveProfiles("test")
@Import(ApplicationInsightsConfiguration::class)
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(ProbationApiGateway::class)],
)
class ProbationApiGatewayTest(
  @Autowired val probationApiGateway: ProbationApiGateway,
  @MockBean val mockHmppsAuthGateway: HmppsAuthGateway,
) : DescribeSpec(
  {
    val probationApiMockServer = ProbationApiMockServer()

    Mockito.`when`(mockHmppsAuthGateway.getClientToken()).thenReturn("mock-bearer-token")

    beforeEach {
      probationApiMockServer.start()
      probationApiMockServer.stubGetOffenderDetails()
    }

    afterTest {
      probationApiMockServer.stop()
    }

    describe("getOffenderName") {
      it("returns the offender name") {
        val offenderName = probationApiGateway.getOffenderName("A999999")
        offenderName.shouldBe("LASTNAME, Firstname")
      }
    }
  },
)
