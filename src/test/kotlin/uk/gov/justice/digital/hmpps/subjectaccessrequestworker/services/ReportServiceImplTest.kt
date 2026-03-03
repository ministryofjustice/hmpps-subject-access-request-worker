package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_SERVICES_SELECTED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
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
  private val telemetryClient: TelemetryClient = mock()

  private val service = ReportServiceImpl(
    htmlRendererApiClient,
    prisonApiClient,
    probationApiClient,
    documentStorageClient,
    serviceConfigurationService,
    pdfService,
    telemetryClient,
  )

  private val suspendedServiceConfig = ServiceConfiguration(
    id = UUID.randomUUID(),
    serviceName = "service-1",
    label = "Service One",
    url = "www.example.com",
    enabled = true,
    category = ServiceCategory.PRISON,
    suspended = true,
    suspendedAt = Instant.now().minus(1, ChronoUnit.DAYS),
  )

  private val unsuspendedServiceConfig = ServiceConfiguration(
    id = UUID.randomUUID(),
    serviceName = "service-1",
    label = "Service One",
    url = "www.example.com",
    enabled = true,
    category = ServiceCategory.PRISON,
    suspended = false,
    suspendedAt = null,
  )

  private val subjectAccessRequest = SubjectAccessRequest(
    id = UUID.fromString("83f1f9af-1036-4273-8252-633f6c7cc1d6"),
    nomisId = "nomis-666",
    ndeliusCaseReferenceId = "ndeliusCaseReferenceId-666",
    sarCaseReferenceNumber = "666",
    dateFrom = LocalDate.parse("2021-01-01"),
    dateTo = LocalDate.parse("2026-01-01"),
    services = "service-1",
  )

  private val pdfRenderRequest = PdfService.PdfRenderRequest(
    subjectAccessRequest = subjectAccessRequest,
    subjectName = "Clive",
  )

  private val pdfByteArray = "Pdf Byte Array".toByteArray()
  private val pdfByteArrayOutputStream = ByteArrayOutputStream().apply { write(pdfByteArray) }

  @Nested
  inner class SuccessScenarios {

    /**
     * ReportServiceImpl is the orchestrator of SAR report generation rather than an "implementation" - test verifies
     * the expected delegated calls are made instead of capturing specific values.
     */
    @Test
    fun `should make expected calls`(): Unit = runBlocking {
      whenever(serviceConfigurationService.getSelectedServices(subjectAccessRequest))
        .thenReturn(listOf(unsuspendedServiceConfig))

      whenever(prisonApiClient.getOffenderName(subjectAccessRequest, subjectAccessRequest.nomisId!!))
        .thenReturn("Clive")

      whenever(pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest))
        .thenReturn(pdfByteArrayOutputStream)

      service.generateReport(subjectAccessRequest)

      verify(serviceConfigurationService, times(1)).getSelectedServices(
        subjectAccessRequest = subjectAccessRequest,
      )
      verify(htmlRendererApiClient, times(1)).submitRenderRequest(
        subjectAccessRequest = subjectAccessRequest,
        serviceConfiguration = unsuspendedServiceConfig,
      )
      verify(prisonApiClient, times(1)).getOffenderName(
        subjectAccessRequest = subjectAccessRequest,
        subjectId = subjectAccessRequest.nomisId,
      )
      verify(pdfService, times(1)).renderSubjectAccessRequestPdf(
        pdfRenderRequest = pdfRenderRequest,
      )
      verify(documentStorageClient, times(1)).storeDocument(
        subjectAccessRequest = subjectAccessRequest,
        docBody = pdfByteArrayOutputStream,
      )
    }
  }

  @Nested
  inner class ErrorScenarios {

    @Test
    fun `should throw exception when selected service is suspended`(): Unit = runBlocking {
      whenever(serviceConfigurationService.getSelectedServices(subjectAccessRequest))
        .thenReturn(listOf(suspendedServiceConfig))

      val actual = assertThrows<SubjectAccessRequestException> { service.generateReport(subjectAccessRequest) }

      assertThat(actual).hasMessageContaining("unable to process request ${suspendedServiceConfig.serviceName} is suspended")
      assertThat(actual.event).isEqualTo(GENERATE_REPORT_SERVICES_SELECTED)
      assertThat(actual.errorCode).isEqualTo(ErrorCode.SERVICE_CONFIGURATION_SUSPENDED)
      assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)

      verify(serviceConfigurationService, times(1)).getSelectedServices(subjectAccessRequest)
      verifyNoMoreInteractions(serviceConfigurationService)
      verifyNoInteractions(
        htmlRendererApiClient,
        prisonApiClient,
        probationApiClient,
        documentStorageClient,
        pdfService,
      )
    }
  }
}
