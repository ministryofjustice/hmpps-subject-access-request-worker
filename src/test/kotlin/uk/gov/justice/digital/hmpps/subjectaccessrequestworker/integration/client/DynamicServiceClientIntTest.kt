package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.web.reactive.function.client.WebClientRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.assertExpectedSubjectAccessRequestExceptionWithCauseNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client.BaseClientIntTest.Companion.StubErrorResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceOneApiExtension.Companion.serviceOneMockApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceTwoApiExtension.Companion.serviceTwoMockApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class DynamicServiceClientIntTest : BaseClientIntTest() {

  companion object {
    const val PRN = "some-prn"
    const val CRN = "some-crn"

    @JvmStatic
    fun responseStubs4xx(): List<StubErrorResponse> = listOf(
      StubErrorResponse(HttpStatus.BAD_REQUEST, WebClientRequestException::class.java),
      StubErrorResponse(HttpStatus.UNAUTHORIZED, WebClientRequestException::class.java),
      StubErrorResponse(HttpStatus.FORBIDDEN, WebClientRequestException::class.java),
    )
  }

  @Autowired
  private lateinit var dynamicServicesClient: DynamicServicesClient

  @Autowired
  private lateinit var oAuth2AuthorizedClientService: OAuth2AuthorizedClientService

  private val subjectAccessRequestParams = GetSubjectAccessRequestParams(
    prn = PRN,
    crn = CRN,
    dateFrom = LocalDate.now().minusDays(1),
    dateTo = LocalDate.now(),
  )

  @BeforeEach
  fun setup() {
    // Remove the cache client token to force each test to obtain an Auth token
    oAuth2AuthorizedClientService.removeAuthorizedClient("sar-client", "anonymousUser")
  }

  @Test
  fun `should get service data`() {
    hmppsAuth.stubGrantToken()
    serviceOneMockApi.stubSubjectAccessRequestSuccessResponse(subjectAccessRequestParams)
    val url = serviceOneMockApi.baseUrl()

    val response =
      dynamicServicesClient.getDataFromService(url, PRN, CRN, LocalDate.now().minusDays(1), LocalDate.now())

    assertThat(response).isNotNull
    assertThat(response!!.body).isEqualTo(mapOf("content" to mapOf("Service One Property" to mapOf("field1" to "value1"))))
  }

  @Test
  fun `should get service data for different services`() {
    hmppsAuth.stubGrantToken()
    serviceOneMockApi.stubSubjectAccessRequestSuccessResponse(subjectAccessRequestParams)
    serviceTwoMockApi.stubSubjectAccessRequestSuccessResponse(subjectAccessRequestParams)
    val serviceOneUrl = serviceOneMockApi.baseUrl()
    val serviceTwoUrl = serviceTwoMockApi.baseUrl()

    val responseOne =
      dynamicServicesClient.getDataFromService(serviceOneUrl, PRN, CRN, LocalDate.now().minusDays(1), LocalDate.now())
    val responseTwo =
      dynamicServicesClient.getDataFromService(serviceTwoUrl, PRN, CRN, LocalDate.now().minusDays(1), LocalDate.now())

    assertThat(responseOne).isNotNull
    assertThat(responseOne!!.body).isEqualTo(mapOf("content" to mapOf("Service One Property" to mapOf("field1" to "value1"))))

    assertThat(responseTwo).isNotNull
    assertThat(responseTwo!!.body).isEqualTo(mapOf("content" to mapOf("Service Two Property" to mapOf("field1" to "value1"))))
  }

  @Test
  fun `should retry on 5xx error and succeed on retry`() {
    hmppsAuth.stubGrantToken()
    serviceOneMockApi.stubSubjectAccessRequestErrorWith5xxOnInitialRequestSucceedOnRetry(subjectAccessRequestParams)
    val url = serviceOneMockApi.baseUrl()

    val response = dynamicServicesClient.getDataFromService(url, PRN, CRN, LocalDate.now().minusDays(1), LocalDate.now())

    assertThat(response).isNotNull
    assertThat(response!!.body).isEqualTo(mapOf("content" to mapOf("Service One Property" to mapOf("field1" to "value1"))))
  }

  @ParameterizedTest
  @MethodSource("responseStubs4xx")
  fun `should not retry on 4xx error`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken()
    serviceOneMockApi.stubSubjectAccessRequestErrorResponse(400, subjectAccessRequestParams)

    val exception = assertThrows<FatalSubjectAccessRequestException> {
      dynamicServicesClient.getDataFromService(serviceOneMockApi.baseUrl(), PRN, CRN, LocalDate.now().minusDays(1), LocalDate.now(), subjectAccessRequest)
    }

    serviceOneMockApi.verifyApiCalled(1)

    assertExpectedSubjectAccessRequestExceptionWithCauseNull(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed with non-retryable error: client 4xx response status",
      expectedEvent = ProcessingEvent.GET_SAR_DATA,
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedParams = mapOf(
        "uri" to expectedUrl(LocalDate.now().minusDays(1), LocalDate.now()),
        "httpStatus" to HttpStatus.BAD_REQUEST,
      ),
    )
  }

  @ParameterizedTest
  @MethodSource("status5xxResponseStubs")
  fun `should retry on 5xx error`(stubResponse: StubErrorResponse) {
    hmppsAuth.stubGrantToken()
    serviceOneMockApi.stubResponseFor(stubResponse.getResponse(), subjectAccessRequestParams)

    val exception = assertThrows<SubjectAccessRequestRetryExhaustedException> {
      dynamicServicesClient.getDataFromService(serviceOneMockApi.baseUrl(), PRN, CRN, LocalDate.now().minusDays(1), LocalDate.now(), subjectAccessRequest)
    }

    serviceOneMockApi.verifyApiCalled(3)

    assertExpectedSubjectAccessRequestException(
      actual = exception,
      expectedPrefix = "subjectAccessRequest failed and max retry attempts (2) exhausted",
      expectedCause = stubResponse.expectedException,
      expectedEvent = ProcessingEvent.GET_SAR_DATA,
      expectedSubjectAccessRequest = subjectAccessRequest,
      expectedParams = mapOf(
        "uri" to serviceOneMockApi.baseUrl(),
      ),
    )
  }

  private val subjectAccessRequest = SubjectAccessRequest(
    id = UUID.randomUUID(),
    sarCaseReferenceNumber = UUID.randomUUID().toString(),
    contextId = UUID.randomUUID(),
  )

  private fun expectedUrl(fromDate: LocalDate, toDate: LocalDate): String {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    return "http://localhost:4100/subject-access-request?prn=$PRN&crn=$CRN&fromDate=${dateFormatter.format(LocalDate.now().minusDays(1))}&toDate=${
      dateFormatter.format(
        LocalDate.now(),
      )
    }"
  }
}

data class GetSubjectAccessRequestParams(
  val prn: String? = null,
  val crn: String? = null,
  val dateFrom: LocalDate? = null,
  val dateTo: LocalDate? = null,
)
