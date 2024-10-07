package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.mockito.internal.verification.VerificationModeFactory
import org.mockito.kotlin.verify
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.ApplicationInsightsConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ComplexityOfNeedMockServer

@ActiveProfiles("test")
@Import(ApplicationInsightsConfiguration::class)
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

      complexityOfNeedMockServer.stubGetSubjectAccessRequestData()
    }

    afterTest {
      complexityOfNeedMockServer.stop()
    }

    describe("getSarData") {
      it("authenticates using HMPPS Auth with credentials") {
        genericHmppsApiGateway.getSarData(
          "http://localhost:4000",
          "validPrn",
        )

        verify(mockHmppsAuthGateway, VerificationModeFactory.times(1)).getClientToken()
      }

      it("retrieves data from the upstream service") {
        val response = genericHmppsApiGateway.getSarData(
          "http://localhost:4000",
          "validPrn",
        )
        println(response)

        response.shouldBe(
          mapOf(
            "content" to mapOf("additionalProp1" to emptyMap<Any, Any>()),
          ),
        )
      }

      it("returns an error if unable to get a response") {
        val exception = shouldThrow<RuntimeException> {
          genericHmppsApiGateway.getSarData(
            "http://localhost:4000",
            "personNotFoundInSystem",
          )
        }

        exception.message.shouldBe("404 Not Found from GET http://localhost:4000/subject-access-request")
      }
    }
  },
)
