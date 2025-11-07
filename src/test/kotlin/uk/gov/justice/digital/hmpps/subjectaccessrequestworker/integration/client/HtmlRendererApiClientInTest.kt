package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.ACQUIRE_AUTH_TOKEN
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testNdeliusCaseReferenceNumber
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testNomisId
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestExceptionWithCauseNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client.BaseClientIntTest.Companion.StubErrorResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.util.UUID

@TestPropertySource(
  properties = [
    "html-renderer.enabled=true",
  ],
)
class HtmlRendererApiClientInTest : BaseClientIntTest() {

  @Autowired
  private lateinit var htmlRendererApiClient: HtmlRendererApiClient

  @Autowired
  private lateinit var webClientConfiguration: WebClientConfiguration

  private val sarDateTo = LocalDate.of(2025, 1, 1)
  private val sarDateFrom = LocalDate.of(2024, 1, 1)
  private val serviceName = "keyworker-api"
  private val serviceUrl = "http://keyworker-api.com"
  private val serviceConfiguration = ServiceConfiguration(serviceName = serviceName, url = serviceUrl, enabled = true, order = 1, label = "Keyworker API")

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token before calling the Prison API.
    clearOauthClientCache("sar-client", "anonymousUser")
  }

  @Test
  fun `should throw expected exception when unable to obtain client auth token`() {
    val subjectAccessRequest = subjectAccessRequest()

    hmppsAuth.stubGrantTokenResponse(
      responseDefinitionBuilder = ResponseDefinitionBuilder().withStatus(403),
    )

    val actual = assertThrows<FatalSubjectAccessRequestException> {
      htmlRendererApiClient.submitRenderRequest(subjectAccessRequest, serviceConfiguration)
    }

    assertThat(actual.message)
      .startsWith("subjectAccessRequest failed with non-retryable error: sarHtmlRendererApiClient error authorization exception")
    assertThat(actual.cause).isNotNull()
    assertThat(actual.cause).isInstanceOf(ClientAuthorizationException::class.java)
    assertThat(actual.event).isEqualTo(ACQUIRE_AUTH_TOKEN)

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderNeverCalled()
  }

  @Test
  fun `should return expected response when request successful`() {
    val subjectAccessRequest = subjectAccessRequest()

    val expectedRequest = HtmlRendererApiClient.HtmlRenderRequest(
      subjectAccessRequest = subjectAccessRequest,
      serviceConfigurationId = serviceConfiguration.id,
    )
    val expectedDocumentKey = "12345-67890"

    hmppsAuth.stubGrantToken()
    htmlRendererApi.stubRenderResponsesWith(
      renderRequest = expectedRequest,
      responseDefinition = rendererSuccessResponse(expectedDocumentKey),
    )

    val response = htmlRendererApiClient.submitRenderRequest(subjectAccessRequest, serviceConfiguration)

    assertThat(response).isNotNull
    assertThat(response!!.documentKey).isEqualTo(expectedDocumentKey)

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(times = 1, expectedBody = expectedRequest)
  }

  @Test
  fun `should be successful when first request fails with 5xx but retry succeeds`() {
    val subjectAccessRequest = subjectAccessRequest()

    val expectedRequest = HtmlRendererApiClient.HtmlRenderRequest(
      subjectAccessRequest = subjectAccessRequest,
      serviceConfigurationId = serviceConfiguration.id,
    )
    val expectedDocumentKey = "12345-67890"

    hmppsAuth.stubGrantToken()
    htmlRendererApi.stubRenderHtmlResponses(
      renderRequest = expectedRequest,
      responseOne = rendererErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR),
      responseTwo = rendererSuccessResponse(expectedDocumentKey),
    )

    val response = htmlRendererApiClient.submitRenderRequest(subjectAccessRequest, serviceConfiguration)

    assertThat(response).isNotNull
    assertThat(response!!.documentKey).isEqualTo(expectedDocumentKey)

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(times = 2, expectedBody = expectedRequest)
  }

  @ParameterizedTest
  @MethodSource("status4xxResponseStubs")
  fun `should not retry when service returns 4xx response status`(stubErrorResponse: StubErrorResponse) {
    val subjectAccessRequest = subjectAccessRequest()

    val expectedRequest = HtmlRendererApiClient.HtmlRenderRequest(
      subjectAccessRequest = subjectAccessRequest,
      serviceConfigurationId = serviceConfiguration.id,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererApi.stubRenderResponsesWith(
      renderRequest = expectedRequest,
      responseDefinition = stubErrorResponse.getResponse(),
    )

    val actual = assertThrows<FatalSubjectAccessRequestException> {
      htmlRendererApiClient.submitRenderRequest(subjectAccessRequest, serviceConfiguration)
    }

    assertExceptedExceptionFor4xxError(actual, subjectAccessRequest, stubErrorResponse)
    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(times = 1, expectedBody = expectedRequest)
  }

  @ParameterizedTest
  @MethodSource("status5xxResponseStubs")
  fun `should retry until max attempts reached when service returns 5xx response status`(stubErrorResponse: StubErrorResponse) {
    val subjectAccessRequest = subjectAccessRequest()

    val expectedRequest = HtmlRendererApiClient.HtmlRenderRequest(
      subjectAccessRequest = subjectAccessRequest,
      serviceConfigurationId = serviceConfiguration.id,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererApi.stubRenderResponsesWith(
      renderRequest = expectedRequest,
      responseDefinition = stubErrorResponse.getResponse(),
    )

    val actual = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      htmlRendererApiClient.submitRenderRequest(subjectAccessRequest, serviceConfiguration)
    }

    assertExceptedExceptionFor5xxError(actual, subjectAccessRequest, stubErrorResponse)
    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(times = 3, expectedBody = expectedRequest)
  }

  private fun subjectAccessRequest() = SubjectAccessRequest(
    id = UUID.randomUUID(),
    dateFrom = sarDateFrom,
    dateTo = sarDateTo,
    sarCaseReferenceNumber = "666",
    services = "some-service",
    nomisId = testNomisId,
    ndeliusCaseReferenceId = testNdeliusCaseReferenceNumber,
    requestedBy = "Me",
    status = Status.Pending,
  )

  private fun assertExceptedExceptionFor4xxError(
    actual: FatalSubjectAccessRequestException,
    subjectAccessRequest: SubjectAccessRequest,
    stubErrorResponse: StubErrorResponse,
  ) = assertExpectedSubjectAccessRequestExceptionWithCauseNull(
    actual = actual,
    expectedPrefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status",
    expectedEvent = ProcessingEvent.HTML_RENDERER_REQUEST,
    expectedSubjectAccessRequest = subjectAccessRequest,
    expectedParams = mapOf(
      "serviceName" to serviceName,
      "serviceUrl" to serviceUrl,
      "uri" to "http://localhost:${htmlRendererApi.port()}/subject-access-request/render",
      "httpStatus" to stubErrorResponse.status,
    ),
  )

  private fun assertExceptedExceptionFor5xxError(
    actual: SubjectAccessRequestRetryExhaustedException,
    subjectAccessRequest: SubjectAccessRequest,
    stubErrorResponse: StubErrorResponse,
  ) = assertExpectedSubjectAccessRequestException(
    actual = actual,
    expectedPrefix = "subjectAccessRequest failed and max retry attempts (${webClientConfiguration.maxRetries}) exhausted",
    expectedEvent = ProcessingEvent.HTML_RENDERER_REQUEST,
    expectedSubjectAccessRequest = subjectAccessRequest,
    expectedCause = stubErrorResponse.expectedException,
    expectedParams = mapOf(
      "serviceName" to serviceName,
      "serviceUrl" to serviceUrl,
    ),
  )
}
