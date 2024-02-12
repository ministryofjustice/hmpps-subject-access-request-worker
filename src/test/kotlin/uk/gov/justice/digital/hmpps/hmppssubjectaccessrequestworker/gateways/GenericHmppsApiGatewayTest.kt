package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.mockito.internal.verification.VerificationModeFactory
import org.mockito.kotlin.verify
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.mockservers.ComplexityOfNeedMockServer
import java.time.LocalDate

@ActiveProfiles("test")
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(GenericHmppsApiGateway::class)],
)
class GenericHmppsApiGatewayTest(
  genericHmppsApiGateway: GenericHmppsApiGateway,
  @MockBean val mockHmppsAuthGateway: HmppsAuthGateway,
) : DescribeSpec(
  {
    val complexityOfNeedMockServer = ComplexityOfNeedMockServer()

    Mockito.`when`(mockHmppsAuthGateway.getClientToken()).thenReturn("mock-bearer-token")

    beforeEach {
      complexityOfNeedMockServer.start()

      complexityOfNeedMockServer.stubGetSubjectAccessRequestData(
        "mockPrn",
        LocalDate.of(2000, 1, 30).toString(),
        LocalDate.of(1999, 1, 30).toString(),
        """
          {
            "content": {
              "additionalProp1": {}
            }
          }
        """.trimIndent(),
      )
    }

    afterTest {
      complexityOfNeedMockServer.stop()
    }

    describe("getSarData") {
      val toDate = LocalDate.of(2000, 1, 30).toString()
      val fromDate = LocalDate.of(1999, 1, 30).toString()

      it("authenticates using HMPPS Auth with credentials") {
        val sarData = genericHmppsApiGateway.getSarData(
          "http://localhost:4000",
          "examplePrn",
          null,
          toDate,
          fromDate,
        )

        verify(mockHmppsAuthGateway, VerificationModeFactory.times(1)).getClientToken()
      }

      it("retrieves data from the upstream service") {
        val sarData = genericHmppsApiGateway.getSarData(
          "http://localhost:4000",
          "examplePrn",
          null,
          toDate,
          fromDate,
        )

        sarData.shouldBe(
          """
          {
            "content": {
              "additionalProp1": {}
            }
          }
          """.trimIndent(),
        )
      }
    }
  },
)
