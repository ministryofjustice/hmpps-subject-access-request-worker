package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient.HtmlRenderResponse
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_RENDER_REQUEST_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_RENDER_REQUEST_FAILED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_SERVICES_SELECTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_SERVICE_SUSPENDED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_SUBMIT_RENDER_REQUEST
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus.COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus.ERRORED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus.PENDING
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus.SUSPENDED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RequestServiceDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ReportServiceImplTest {

  private val htmlRendererApiClient: HtmlRendererApiClient = mock()
  private val prisonApiClient: PrisonApiClient = mock()
  private val probationApiClient: ProbationApiClient = mock()
  private val documentStorageClient: DocumentStorageClient = mock()
  private val serviceConfigurationService: ServiceConfigurationService = mock()
  private val pdfService: PdfService = mock()
  private val subjectAccessRequestService: SubjectAccessRequestService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val service = ReportServiceImpl(
    htmlRendererApiClient,
    prisonApiClient,
    probationApiClient,
    documentStorageClient,
    pdfService,
    subjectAccessRequestService,
    telemetryClient,
  )

  private val suspendedServiceConfig = ServiceConfiguration(
    id = UUID.randomUUID(),
    serviceName = "service-1",
    label = "Service One",
    url = "www.example.com",
    enabled = true,
    templateMigrated = true,
    category = ServiceCategory.PRISON,
    suspended = true,
    suspendedAt = Instant.now().minus(1, ChronoUnit.DAYS),
  )

  private val unsuspendedServiceConfig = createUnsuspendedServiceConfig("service-1", "Service One")
  private val unsuspendedServiceConfigTwo = createUnsuspendedServiceConfig("service-2", "Service Two")
  private val unsuspendedServiceConfigThree = createUnsuspendedServiceConfig("service-3", "Service Three")
  private val unsuspendedServiceConfigFour = createUnsuspendedServiceConfig("service-4", "Service Four")
  private val unsuspendedServiceConfigFive = createUnsuspendedServiceConfig("service-5", "Service Five")
  private val unsuspendedServiceConfigSix = createUnsuspendedServiceConfig("service-6", "Service Six")

  private val subjectAccessRequest = SubjectAccessRequest(
    id = UUID.fromString("83f1f9af-1036-4273-8252-633f6c7cc1d6"),
    nomisId = "nomis-666",
    ndeliusCaseReferenceId = "ndeliusCaseReferenceId-666",
    sarCaseReferenceNumber = "666",
    dateFrom = LocalDate.parse("2021-01-01"),
    dateTo = LocalDate.parse("2026-01-01"),
    services = mutableListOf(),
  )

  private val pdfRenderRequest = PdfService.PdfRenderRequest(
    subjectAccessRequest = subjectAccessRequest,
    subjectName = "Clive",
  )

  private val pdfByteArray = "Pdf Byte Array".toByteArray()

  private val pdfByteArrayOutputStream = ByteArrayOutputStream().apply { write(pdfByteArray) }

  @BeforeEach
  fun setup() {
    subjectAccessRequest.services.clear()
  }

  @AfterEach
  fun afterEach() {
    verifyNoMoreInteractions(
      htmlRendererApiClient,
      prisonApiClient,
      probationApiClient,
      documentStorageClient,
      serviceConfigurationService,
      pdfService,
      subjectAccessRequestService,
      telemetryClient,
    )
  }

  @Nested
  inner class SuccessScenarios {
    /**
     * ReportServiceImpl is the orchestrator of SAR report generation rather than an "implementation" - test verifies
     * the expected delegated calls are made instead of capturing specific values.
     */
    @Test
    fun `should make expected calls`(): Unit = runBlocking {
      subjectAccessRequest.services.addAll(
        listOf(
          createRequestServiceDetail(unsuspendedServiceConfig, PENDING),
          createRequestServiceDetail(unsuspendedServiceConfigTwo, ERRORED),
          createRequestServiceDetail(unsuspendedServiceConfigThree, COMPLETE),
          createRequestServiceDetail(unsuspendedServiceConfigFour, PENDING),
          createRequestServiceDetail(unsuspendedServiceConfigFive, COMPLETE),
          createRequestServiceDetail(unsuspendedServiceConfigSix, SUSPENDED),
        ),
      )

      givenRenderRequestReturnsVersion(unsuspendedServiceConfig, "1")
      givenRenderRequestReturnsVersion(unsuspendedServiceConfigTwo, "2")
      givenRenderRequestReturnsVersion(unsuspendedServiceConfigFour, "3")
      givenRenderRequestReturnsVersion(unsuspendedServiceConfigSix, "4")
      whenever(prisonApiClient.getOffenderName(subjectAccessRequest, subjectAccessRequest.nomisId!!))
        .thenReturn("Clive")
      whenever(pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest)).thenReturn(pdfByteArrayOutputStream)

      service.generateReport(subjectAccessRequest)

      verify(htmlRendererApiClient).submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfig)
      verify(htmlRendererApiClient).submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfigTwo)
      verify(htmlRendererApiClient).submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfigFour)
      verify(htmlRendererApiClient).submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfigSix)
      thenServiceStatusUpdatedAsSuccessFor(unsuspendedServiceConfig, "1")
      thenServiceStatusUpdatedAsSuccessFor(unsuspendedServiceConfigTwo, "2")
      thenServiceStatusUpdatedAsSuccessFor(unsuspendedServiceConfigFour, "3")
      thenServiceStatusUpdatedAsSuccessFor(unsuspendedServiceConfigSix, "4")
      verify(subjectAccessRequestService).validateAllServicesRendered(subjectAccessRequest.id)
      verify(prisonApiClient).getOffenderName(subjectAccessRequest, subjectAccessRequest.nomisId!!)
      verify(pdfService).renderSubjectAccessRequestPdf(pdfRenderRequest)
      verify(documentStorageClient).storeDocument(subjectAccessRequest, pdfByteArrayOutputStream)
      thenEventTrackedForServicesSelected("service-4,service-1,service-6,service-2")
      thenEventTrackedForSubmitRenderRequest(unsuspendedServiceConfig)
      thenEventTrackedForRenderRequestCompleted(unsuspendedServiceConfig)
      thenEventTrackedForSubmitRenderRequest(unsuspendedServiceConfigTwo)
      thenEventTrackedForRenderRequestCompleted(unsuspendedServiceConfigTwo)
      thenEventTrackedForSubmitRenderRequest(unsuspendedServiceConfigFour)
      thenEventTrackedForRenderRequestCompleted(unsuspendedServiceConfigFour)
      thenEventTrackedForSubmitRenderRequest(unsuspendedServiceConfigSix)
      thenEventTrackedForRenderRequestCompleted(unsuspendedServiceConfigSix)
    }
  }

  @Nested
  inner class ErrorScenarios {
    @Test
    fun `should throw exception when selected service is suspended`(): Unit = runBlocking {
      subjectAccessRequest.services.addAll(
        listOf(
          createRequestServiceDetail(unsuspendedServiceConfigTwo, PENDING),
          createRequestServiceDetail(suspendedServiceConfig, PENDING),
          createRequestServiceDetail(unsuspendedServiceConfigThree, PENDING),
        ),
      )
      givenRenderRequestReturnsVersion(unsuspendedServiceConfigTwo, "1")
      givenRenderRequestReturnsVersion(unsuspendedServiceConfigThree, "2")
      val exception = SubjectAccessRequestException("test error")
      whenever(subjectAccessRequestService.validateAllServicesRendered(subjectAccessRequest.id)).thenThrow(exception)

      val actual = assertThrows<SubjectAccessRequestException> { service.generateReport(subjectAccessRequest) }

      assertThat(actual).isEqualTo(exception)
      verify(htmlRendererApiClient).submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfigTwo)
      verify(htmlRendererApiClient).submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfigThree)
      thenServiceStatusUpdatedAsSuccessFor(unsuspendedServiceConfigTwo, "1")
      thenServiceStatusUpdatedAsSuccessFor(unsuspendedServiceConfigThree, "2")
      verify(subjectAccessRequestService).updateServiceStatusSuspended(subjectAccessRequest.id, suspendedServiceConfig.serviceName)
      verify(subjectAccessRequestService).validateAllServicesRendered(subjectAccessRequest.id)
      thenEventTrackedForServicesSelected("service-1,service-3,service-2")
      thenEventTrackedForSubmitRenderRequest(unsuspendedServiceConfigTwo)
      thenEventTrackedForRenderRequestCompleted(unsuspendedServiceConfigTwo)
      thenEventTrackedForSubmitRenderRequest(unsuspendedServiceConfigThree)
      thenEventTrackedForRenderRequestCompleted(unsuspendedServiceConfigThree)
      thenEventTrackedForServiceSuspended(suspendedServiceConfig)
    }

    @Test
    fun `should throw exception and mark service as errored when render request fails`(): Unit = runBlocking {
      subjectAccessRequest.services.addAll(
        listOf(
          createRequestServiceDetail(unsuspendedServiceConfig, PENDING),
          createRequestServiceDetail(unsuspendedServiceConfigTwo, PENDING),
          createRequestServiceDetail(unsuspendedServiceConfigThree, PENDING),
        ),
      )
      givenRenderRequestReturnsVersion(unsuspendedServiceConfig, "1")
      val exception = SubjectAccessRequestException("test")
      whenever(htmlRendererApiClient.submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfigTwo)).thenThrow(
        exception,
      )
      givenRenderRequestReturnsVersion(unsuspendedServiceConfigThree, "3")
      whenever(subjectAccessRequestService.validateAllServicesRendered(subjectAccessRequest.id)).thenThrow(exception)

      val actual = assertThrows<SubjectAccessRequestException> { service.generateReport(subjectAccessRequest) }

      assertThat(actual).isEqualTo(exception)

      verify(htmlRendererApiClient).submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfig)
      verify(htmlRendererApiClient).submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfigTwo)
      verify(htmlRendererApiClient).submitRenderRequest(subjectAccessRequest, unsuspendedServiceConfigThree)
      thenServiceStatusUpdatedAsSuccessFor(unsuspendedServiceConfig, "1")
      thenServiceStatusUpdatedAsSuccessFor(unsuspendedServiceConfigThree, "3")
      verify(subjectAccessRequestService).updateServiceStatusFailed(
        subjectAccessRequest.id,
        unsuspendedServiceConfigTwo.serviceName,
      )
      verify(subjectAccessRequestService).validateAllServicesRendered(subjectAccessRequest.id)
      thenEventTrackedForServicesSelected("service-1,service-3,service-2")
      thenEventTrackedForSubmitRenderRequest(unsuspendedServiceConfig)
      thenEventTrackedForRenderRequestCompleted(unsuspendedServiceConfig)
      thenEventTrackedForSubmitRenderRequest(unsuspendedServiceConfigTwo)
      thenEventTrackedForRenderRequestFailed(unsuspendedServiceConfigTwo)
      thenEventTrackedForSubmitRenderRequest(unsuspendedServiceConfigThree)
      thenEventTrackedForRenderRequestCompleted(unsuspendedServiceConfigThree)
    }
  }

  private fun createRequestServiceDetail(
    serviceConfiguration: ServiceConfiguration,
    renderStatus: RenderStatus,
  ): RequestServiceDetail = RequestServiceDetail(
    subjectAccessRequest = subjectAccessRequest,
    serviceConfiguration = serviceConfiguration,
    renderStatus = renderStatus,
  )

  private fun createUnsuspendedServiceConfig(name: String, label: String): ServiceConfiguration = ServiceConfiguration(
    id = UUID.randomUUID(),
    serviceName = name,
    label = label,
    url = "www.example.com",
    enabled = true,
    templateMigrated = true,
    category = ServiceCategory.PRISON,
    suspended = false,
    suspendedAt = null,
  )

  private fun givenRenderRequestReturnsVersion(serviceConfiguration: ServiceConfiguration, templateVersion: String) {
    whenever(htmlRendererApiClient.submitRenderRequest(subjectAccessRequest, serviceConfiguration)).thenReturn(
      HtmlRenderResponse("documentKey", templateVersion),
    )
  }

  private fun thenServiceStatusUpdatedAsSuccessFor(serviceConfiguration: ServiceConfiguration, templateVersion: String) {
    verify(subjectAccessRequestService).updateServiceStatusSuccess(
      subjectAccessRequest.id,
      serviceConfiguration.serviceName,
      templateVersion,
    )
  }

  private fun thenEventTrackedForServicesSelected(services: String) {
    verify(telemetryClient).trackEvent(
      GENERATE_REPORT_SERVICES_SELECTED.toString(),
      mapOf(
        "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
        "UUID" to subjectAccessRequest.id.toString(),
        "contextId" to subjectAccessRequest.contextId.toString(),
        "services" to services,
      ),
      null,
    )
  }

  private fun thenEventTrackedForSubmitRenderRequest(serviceConfiguration: ServiceConfiguration) {
    verify(telemetryClient).trackEvent(
      GENERATE_REPORT_SUBMIT_RENDER_REQUEST.toString(),
      mapOf(
        "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
        "UUID" to subjectAccessRequest.id.toString(),
        "contextId" to subjectAccessRequest.contextId.toString(),
        "serviceName" to serviceConfiguration.serviceName,
        "serviceUrl" to serviceConfiguration.url,
      ),
      null,
    )
  }

  private fun thenEventTrackedForRenderRequestCompleted(serviceConfiguration: ServiceConfiguration) {
    verify(telemetryClient).trackEvent(
      GENERATE_REPORT_RENDER_REQUEST_COMPLETED.toString(),
      mapOf(
        "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
        "UUID" to subjectAccessRequest.id.toString(),
        "contextId" to subjectAccessRequest.contextId.toString(),
        "serviceName" to serviceConfiguration.serviceName,
        "serviceUrl" to serviceConfiguration.url,
      ),
      null,
    )
  }

  private fun thenEventTrackedForRenderRequestFailed(serviceConfiguration: ServiceConfiguration) {
    verify(telemetryClient).trackEvent(
      GENERATE_REPORT_RENDER_REQUEST_FAILED.toString(),
      mapOf(
        "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
        "UUID" to subjectAccessRequest.id.toString(),
        "contextId" to subjectAccessRequest.contextId.toString(),
        "serviceName" to serviceConfiguration.serviceName,
        "serviceUrl" to serviceConfiguration.url,
      ),
      null,
    )
  }

  private fun thenEventTrackedForServiceSuspended(serviceConfiguration: ServiceConfiguration) {
    verify(telemetryClient).trackEvent(
      GENERATE_REPORT_SERVICE_SUSPENDED.toString(),
      mapOf(
        "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
        "UUID" to subjectAccessRequest.id.toString(),
        "contextId" to subjectAccessRequest.contextId.toString(),
        "serviceName" to serviceConfiguration.serviceName,
      ),
      null,
    )
  }
}
