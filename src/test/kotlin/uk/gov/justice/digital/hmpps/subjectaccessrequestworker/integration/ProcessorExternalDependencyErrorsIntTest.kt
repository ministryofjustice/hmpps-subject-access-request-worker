package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.jsonResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.atLeastOnce
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting.AlertsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient.HtmlRenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestRetryExhaustedException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.NO_SUBJECT_ID_PROVIDED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testDateFrom
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testDateTo
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testNdeliusCaseReferenceNumber
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testNomisId
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ProbationApiExtension.Companion.probationApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RequestServiceDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.util.UUID
import java.util.concurrent.TimeUnit

class ProcessorExternalDependencyErrorsIntTest : BaseProcessorIntTest() {

  private val alertCaptor = argumentCaptor<SubjectAccessRequestException>()
  private var serviceConfig: ServiceConfiguration? = null
  private var serviceConfigTwo: ServiceConfiguration? = null
  private var serviceConfigThree: ServiceConfiguration? = null

  @MockitoBean
  protected lateinit var alertService: AlertsService

  data class TestCase<T : SubjectAccessRequestException>(
    val errorCodeStr: String?,
    val status: Int,
    val errorCode: ErrorCode,
    val event: ProcessingEvent,
    val exceptionType: Class<T>,
  )

  @BeforeEach
  fun setup() {
    serviceConfig = getServiceConfiguration("hmpps-book-secure-move-api")
    serviceConfigTwo = getServiceConfiguration("hmpps-education-and-work-plan-api")
    serviceConfigThree = getServiceConfiguration("hmpps-non-associations-api")
  }

  @AfterEach
  fun cleanup() {
    subjectAccessRequestRepository.deleteAll()
  }

  @ParameterizedTest
  @MethodSource("rendererErrorScenarios")
  fun `should raise expected alert when HTML Renderer returns error status`(
    testCase: TestCase<SubjectAccessRequestException>,
  ): Unit = runBlocking {
    val subjectAccessRequest = createPrisonSubjectAccessRequest()
    val htmlRendererRequest = createHtmlRenderRequest(subjectAccessRequest)

    hmppsAuth.stubGrantToken()
    stubHtmlRendererError(htmlRendererRequest, testCase)

    executeTest()

    assertRaisedExceptionAlert(
      expectedExceptionType = testCase.exceptionType,
      expectedErrorCode = testCase.errorCode,
      expectedEvent = testCase.event,
      expectedSAR = subjectAccessRequest,
    )

    verifyHtmlRendererIsCalled(htmlRendererRequest)
  }

  @Test
  fun `should raise expected alert when multiple services and HTML Renderer returns error status for one`(): Unit = runBlocking {
    val subjectAccessRequest = createPrisonSubjectAccessRequest(listOf(serviceConfig!!, serviceConfigTwo!!, serviceConfigThree!!))
    val htmlRendererRequestOne = createHtmlRenderRequest(subjectAccessRequest, serviceConfig!!)
    val htmlRendererRequestTwo = createHtmlRenderRequest(subjectAccessRequest, serviceConfigTwo!!)
    val htmlRendererRequestThree = createHtmlRenderRequest(subjectAccessRequest, serviceConfigThree!!)

    hmppsAuth.stubGrantToken()
    stubHtmlRendererSuccess(htmlRendererRequestOne)
    stubHtmlRendererError(htmlRendererRequestTwo, "3001", 500)
    stubHtmlRendererSuccess(htmlRendererRequestThree)

    executeTest()

    assertRaisedExceptionAlert(
      expectedExceptionType = SubjectAccessRequestException::class.java,
      expectedErrorCode = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10106"),
      expectedEvent = ProcessingEvent.GENERATE_REPORT_RENDER_ALL_COMPLETED,
      expectedSAR = subjectAccessRequest,
      params = mapOf("services" to mapOf("hmpps-education-and-work-plan-api" to RenderStatus.ERRORED)),
    )

    verifyHtmlRendererIsCalled(htmlRendererRequestOne)
    verifyHtmlRendererIsCalled(htmlRendererRequestTwo)
    verifyHtmlRendererIsCalled(htmlRendererRequestThree)
  }

  @ParameterizedTest
  @MethodSource("prisonApiErrorScenarios")
  fun `should raise expected alert when Prison API returns error status`(
    testCase: TestCase<SubjectAccessRequestException>,
  ): Unit = runBlocking {
    val subjectAccessRequest = createPrisonSubjectAccessRequest()
    val htmlRendererRequest = createHtmlRenderRequest(subjectAccessRequest)

    hmppsAuth.stubGrantToken()
    stubHtmlRendererSuccess()

    prisonApi.stubResponseFor(
      subjectId = subjectAccessRequest.nomisId!!,
      response = errorResponseDefinition(
        testCase.status,
        testCase.errorCodeStr,
      ),
    )

    executeTest()

    assertRaisedExceptionAlert(
      expectedExceptionType = testCase.exceptionType,
      expectedErrorCode = testCase.errorCode,
      expectedEvent = testCase.event,
      expectedSAR = subjectAccessRequest,
    )

    verifyHtmlRendererIsCalled(htmlRendererRequest)
    verifyPrisonApiIsCalled(subjectAccessRequest.nomisId)
  }

  @ParameterizedTest
  @MethodSource("probationApiErrorScenarios")
  fun `should raise expected alert when Probation API returns error status`(
    testCase: TestCase<SubjectAccessRequestException>,
  ): Unit = runBlocking {
    val subjectAccessRequest = createProbationSubjectAccessRequest()
    val htmlRendererRequest = createHtmlRenderRequest(subjectAccessRequest)

    hmppsAuth.stubGrantToken()
    stubHtmlRendererSuccess()

    probationApi.stubResponseFor(
      subjectId = subjectAccessRequest.ndeliusCaseReferenceId!!,
      errorResponseDefinition(
        testCase.status,
        testCase.errorCodeStr,
      ),
    )

    executeTest()

    assertRaisedExceptionAlert(
      expectedExceptionType = testCase.exceptionType,
      expectedErrorCode = testCase.errorCode,
      expectedEvent = testCase.event,
      expectedSAR = subjectAccessRequest,
    )

    verifyHtmlRendererIsCalled(htmlRendererRequest)
    verifyProbationApiIsCalled(subjectAccessRequest.ndeliusCaseReferenceId)
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "   | ",
      "'' | ",
      "   |'' ",
      "'' |'' ",
    ],
    delimiterString = "|",
  )
  fun `should raise expected alert when NomisID and Ndelius IDs are null`(
    nomisId: String?,
    ndeliusId: String?,
  ): Unit = runBlocking {
    val subjectAccessRequest = SubjectAccessRequest(
      id = UUID.randomUUID(),
      dateFrom = testDateFrom,
      dateTo = testDateTo,
      sarCaseReferenceNumber = "666",
      services = mutableListOf(),
      nomisId = nomisId,
      ndeliusCaseReferenceId = ndeliusId,
      requestedBy = "Me",
      status = Status.Pending,
    )
    subjectAccessRequest.services.add(
      RequestServiceDetail(
        subjectAccessRequest = subjectAccessRequest,
        serviceConfiguration = serviceConfig!!,
        renderStatus = RenderStatus.PENDING,
      ),
    )
    subjectAccessRequestRepository.save(subjectAccessRequest)
    val htmlRendererRequest = createHtmlRenderRequest(subjectAccessRequest)

    hmppsAuth.stubGrantToken()
    stubHtmlRendererSuccess()

    executeTest()

    assertRaisedExceptionAlert(
      expectedExceptionType = FatalSubjectAccessRequestException::class.java,
      expectedErrorCode = NO_SUBJECT_ID_PROVIDED,
      expectedEvent = ProcessingEvent.RESOLVE_SUBJECT_NAME,
      expectedSAR = subjectAccessRequest,
    )

    verifyHtmlRendererIsCalled(htmlRendererRequest)
  }

  private fun createPrisonSubjectAccessRequest(): SubjectAccessRequest = createPrisonSubjectAccessRequest(listOf(serviceConfig!!))

  private fun createPrisonSubjectAccessRequest(services: List<ServiceConfiguration>): SubjectAccessRequest {
    val subjectAccessRequest = SubjectAccessRequest(
      id = UUID.randomUUID(),
      dateFrom = testDateFrom,
      dateTo = testDateTo,
      sarCaseReferenceNumber = "666",
      services = mutableListOf(),
      nomisId = testNomisId,
      ndeliusCaseReferenceId = null,
      requestedBy = "Me",
      status = Status.Pending,
    )
    subjectAccessRequest.services.addAll(
      services.map {
        RequestServiceDetail(
          subjectAccessRequest = subjectAccessRequest,
          serviceConfiguration = it,
          renderStatus = RenderStatus.PENDING,
        )
      },
    )
    subjectAccessRequestRepository.save(subjectAccessRequest)
    return subjectAccessRequest
  }

  private fun createProbationSubjectAccessRequest(): SubjectAccessRequest {
    val subjectAccessRequest = SubjectAccessRequest(
      id = UUID.randomUUID(),
      dateFrom = testDateFrom,
      dateTo = testDateTo,
      sarCaseReferenceNumber = "666",
      services = mutableListOf(),
      nomisId = null,
      ndeliusCaseReferenceId = testNdeliusCaseReferenceNumber,
      requestedBy = "Me",
      status = Status.Pending,
    )
    subjectAccessRequest.services.add(
      RequestServiceDetail(
        subjectAccessRequest = subjectAccessRequest,
        serviceConfiguration = serviceConfig!!,
        renderStatus = RenderStatus.PENDING,
      ),
    )
    subjectAccessRequestRepository.save(subjectAccessRequest)
    return subjectAccessRequest
  }

  private fun createHtmlRenderRequest(sar: SubjectAccessRequest) = createHtmlRenderRequest(sar, serviceConfig!!)

  private fun createHtmlRenderRequest(sar: SubjectAccessRequest, serviceConfiguration: ServiceConfiguration) = HtmlRenderRequest(
    subjectAccessRequest = sar,
    serviceConfigurationId = serviceConfiguration.id,
  )

  private fun executeTest() = runBlocking {
    sarProcessor.execute()

    await()
      .atMost(8, TimeUnit.SECONDS)
      .pollInterval(500, TimeUnit.MILLISECONDS)
      .untilAsserted {
        verify(alertService, atLeastOnce()).raiseReportErrorAlert(alertCaptor.capture())
      }
  }

  private fun <T : SubjectAccessRequestException> assertRaisedExceptionAlert(
    expectedErrorCode: ErrorCode,
    expectedEvent: ProcessingEvent,
    expectedExceptionType: Class<T>,
    expectedSAR: SubjectAccessRequest,
    params: Map<String, *>,
  ) {
    val exception = assertRaisedExceptionAlert(expectedErrorCode, expectedEvent, expectedExceptionType, expectedSAR)
    assertThat(exception.params).isEqualTo(params)
  }

  private fun <T : SubjectAccessRequestException> assertRaisedExceptionAlert(
    expectedErrorCode: ErrorCode,
    expectedEvent: ProcessingEvent,
    expectedExceptionType: Class<T>,
    expectedSAR: SubjectAccessRequest,
  ): SubjectAccessRequestException {
    assertThat(alertCaptor.allValues).hasSize(1)
    val exception = alertCaptor.firstValue

    assertThat(exception).isInstanceOf(expectedExceptionType)
    assertThat(exception.event).isEqualTo(expectedEvent)
    assertThat(exception.errorCode).isEqualTo(expectedErrorCode)
    assertThat(exception.subjectAccessRequest!!.id).isEqualTo(expectedSAR.id)
    return exception
  }

  private fun stubHtmlRendererError(htmlRenderRequest: HtmlRenderRequest, testCase: TestCase<*>) {
    stubHtmlRendererError(htmlRenderRequest, testCase.errorCodeStr, testCase.status)
  }

  private fun stubHtmlRendererError(htmlRenderRequest: HtmlRenderRequest, errorCodeStr: String?, status: Int) {
    htmlRendererApi.stubRenderResponsesWith(htmlRenderRequest, errorResponseDefinition(status, errorCodeStr))
  }

  private fun stubHtmlRendererSuccess() {
    htmlRendererApi.stubFor(
      post(urlPathEqualTo("/subject-access-request/render")).willReturn(
        jsonResponse("""{"templateVersion":"1"}""", 201),
      ),
    )
  }

  private fun stubHtmlRendererSuccess(htmlRenderRequest: HtmlRenderRequest) {
    htmlRendererApi.stubRenderResponsesWith(htmlRenderRequest, jsonResponse("""{"templateVersion":"1"}""", 201))
  }

  private fun verifyHtmlRendererIsCalled(expectedHtmlRenderRequest: HtmlRenderRequest) {
    htmlRendererApi.verify(
      postRequestedFor(urlPathEqualTo("/subject-access-request/render")).withRequestBody(
        equalToJson(
          objectMapper.writeValueAsString(
            expectedHtmlRenderRequest,
          ),
        ),
      ),
    )
  }

  private fun verifyPrisonApiIsCalled(subjectId: String) {
    prisonApi.verify(
      getRequestedFor(urlPathEqualTo("/api/offenders/$subjectId")),
    )
  }

  private fun verifyProbationApiIsCalled(subjectId: String) {
    probationApi.verify(
      getRequestedFor(urlPathEqualTo("/probation-case/$subjectId")),
    )
  }

  companion object {

    @JvmStatic
    fun rendererErrorScenarios(): List<TestCase<out SubjectAccessRequestException>> = listOf(
      TestCase(
        errorCodeStr = "3001",
        status = 500,
        errorCode = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10106"),
        event = ProcessingEvent.GENERATE_REPORT_RENDER_ALL_COMPLETED,
        exceptionType = SubjectAccessRequestException::class.java,
      ),
      TestCase(
        errorCodeStr = "3002",
        status = 500,
        errorCode = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10106"),
        event = ProcessingEvent.GENERATE_REPORT_RENDER_ALL_COMPLETED,
        exceptionType = SubjectAccessRequestException::class.java,
      ),
      TestCase(
        errorCodeStr = "3003",
        status = 500,
        errorCode = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10106"),
        event = ProcessingEvent.GENERATE_REPORT_RENDER_ALL_COMPLETED,
        exceptionType = SubjectAccessRequestException::class.java,
      ),
      TestCase(
        errorCodeStr = "401",
        status = 401,
        errorCode = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10106"),
        event = ProcessingEvent.GENERATE_REPORT_RENDER_ALL_COMPLETED,
        exceptionType = SubjectAccessRequestException::class.java,
      ),
      TestCase(
        errorCodeStr = "403",
        status = 403,
        errorCode = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10106"),
        event = ProcessingEvent.GENERATE_REPORT_RENDER_ALL_COMPLETED,
        exceptionType = SubjectAccessRequestException::class.java,
      ),
      TestCase(
        errorCodeStr = "409",
        status = 409,
        errorCode = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10106"),
        event = ProcessingEvent.GENERATE_REPORT_RENDER_ALL_COMPLETED,
        exceptionType = SubjectAccessRequestException::class.java,
      ),
    )

    @JvmStatic
    fun prisonApiErrorScenarios(): List<TestCase<out SubjectAccessRequestException>> = listOf(
      TestCase(
        errorCodeStr = "401",
        status = 401,
        errorCode = ErrorCode(ErrorCodePrefix.PRISON_API, "401"),
        event = ProcessingEvent.GET_OFFENDER_NAME,
        exceptionType = FatalSubjectAccessRequestException::class.java,
      ),
      TestCase(
        errorCodeStr = "403",
        status = 403,
        errorCode = ErrorCode(ErrorCodePrefix.PRISON_API, "403"),
        event = ProcessingEvent.GET_OFFENDER_NAME,
        exceptionType = FatalSubjectAccessRequestException::class.java,
      ),
      TestCase(
        errorCodeStr = "ZYX666",
        status = 500,
        errorCode = ErrorCode(ErrorCodePrefix.PRISON_API, "ZYX666"),
        event = ProcessingEvent.GET_OFFENDER_NAME,
        exceptionType = SubjectAccessRequestRetryExhaustedException::class.java,
      ),
    )

    @JvmStatic
    fun probationApiErrorScenarios(): List<TestCase<out SubjectAccessRequestException>> = listOf(
      // 4xx cases should always use status value for error code
      TestCase(
        errorCodeStr = null,
        status = 401,
        errorCode = ErrorCode(ErrorCodePrefix.PROBATION_API, "401"),
        event = ProcessingEvent.GET_OFFENDER_NAME,
        exceptionType = FatalSubjectAccessRequestException::class.java,
      ),
      // 4xx cases should always use status value for error code
      TestCase(
        errorCodeStr = "someRandomCode",
        status = 401,
        errorCode = ErrorCode(ErrorCodePrefix.PROBATION_API, "401"),
        event = ProcessingEvent.GET_OFFENDER_NAME,
        exceptionType = FatalSubjectAccessRequestException::class.java,
      ),
      // 5XX cases should use status if code is empty or null
      TestCase(
        errorCodeStr = null,
        status = 500,
        errorCode = ErrorCode(ErrorCodePrefix.PROBATION_API, "500"),
        event = ProcessingEvent.GET_OFFENDER_NAME,
        exceptionType = SubjectAccessRequestRetryExhaustedException::class.java,
      ),
      // 5XX cases should use code if not empty or null
      TestCase(
        errorCodeStr = "someRandomCode",
        status = 500,
        errorCode = ErrorCode(ErrorCodePrefix.PROBATION_API, "someRandomCode"),
        event = ProcessingEvent.GET_OFFENDER_NAME,
        exceptionType = SubjectAccessRequestRetryExhaustedException::class.java,
      ),
    )
  }
}
