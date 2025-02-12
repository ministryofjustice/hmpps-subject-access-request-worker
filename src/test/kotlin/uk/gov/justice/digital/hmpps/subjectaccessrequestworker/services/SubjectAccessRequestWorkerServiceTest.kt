package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfWriter
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class SubjectAccessRequestWorkerServiceTest : IntegrationTestBase() {
  private val dateFromFormatted = LocalDate.parse("02/01/2023", DateTimeFormatter.ofPattern("dd/MM/yyyy"))
  private val dateToFormatted = LocalDate.parse("02/01/2024", DateTimeFormatter.ofPattern("dd/MM/yyyy"))
  private val documentStorageClient: DocumentStorageClient = mock()
  private val sampleSAR = SubjectAccessRequest(
    id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
    status = Status.Pending,
    dateFrom = dateFromFormatted,
    dateTo = dateToFormatted,
    sarCaseReferenceNumber = "1234abc",
    services = "service-a, https://service-a.hmpps.service.justice.gov.uk,service-b, https://service-b.hmpps.service.justice.gov.uk",
    nomisId = null,
    ndeliusCaseReferenceId = "1",
    requestedBy = "aName",
    requestDateTime = LocalDateTime.now(),
    claimAttempts = 0,
  )
  private val dpsServicesList = listOf(DpsService(), DpsService())

  val selectedDpsServices =
    mutableListOf(
      DpsService(
        name = "service-a",
        url = "https://service-a.hmpps.service.justice.gov.uk",
        businessName = null,
        orderPosition = null,
      ),
      DpsService(
        name = "service-b",
        url = "https://service-b.hmpps.service.justice.gov.uk",
        businessName = null,
        orderPosition = null,
      ),
    )

  private val getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService = mock()
  private val prisonApiClient: PrisonApiClient = mock()
  private val probationApiClient: ProbationApiClient = mock()
  private val generatePdfService: GeneratePdfService = mock()
  private val byteArrayOutputStream: ByteArrayOutputStream = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val serviceConfigurationService: ServiceConfigurationService = mock()
  private val pdfWriter: PdfWriter = mock()
  private val subjectAccessRequestService: SubjectAccessRequestService = mock()

  val subjectAccessRequestWorkerService = SubjectAccessRequestWorkerService(
    getSubjectAccessRequestDataService,
    documentStorageClient,
    generatePdfService,
    prisonApiClient,
    probationApiClient,
    serviceConfigurationService,
    subjectAccessRequestService,
    telemetryClient,
  )

  private val postDocumentResponse = DocumentStorageClient.PostDocumentResponse(documentUuid = sampleSAR.id.toString())

  @Test
  fun `pollForNewSubjectAccessRequests returns single SubjectAccessRequest`() = runTest {
    whenever(subjectAccessRequestService.findUnclaimed()).thenReturn(listOf(sampleSAR))

    val result = subjectAccessRequestWorkerService
      .pollForNewSubjectAccessRequests()

    val expected: SubjectAccessRequest = sampleSAR
    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `pollForRequests polls for unclaimed SAR`() = runTest {
    whenever(subjectAccessRequestService.findUnclaimed()).thenReturn(listOf(sampleSAR))

    subjectAccessRequestWorkerService.pollForRequests()

    verify(subjectAccessRequestService, times(1)).findUnclaimed()
  }

  @Test
  fun `startPolling calls claim and complete on happy path`() = runTest {
    whenever(serviceConfigurationService.getSelectedServices(any())).thenReturn(selectedDpsServices)

    whenever(
      getSubjectAccessRequestDataService.requestDataFromServices(
        selectedDpsServices,
        null,
        "1",
        dateFromFormatted,
        dateToFormatted,
        sampleSAR,
      ),
    )
      .thenReturn(dpsServicesList)
    whenever(generatePdfService.getPdfWriter(byteArrayOutputStream))
      .thenReturn(pdfWriter)
    whenever(probationApiClient.getOffenderName(sampleSAR, "1"))
      .thenReturn("TEST, Name")
    whenever(
      generatePdfService.execute(
        services = dpsServicesList,
        subjectName = "TEST, Name",
        sar = sampleSAR,
      ),
    ).thenReturn(byteArrayOutputStream)

    whenever(documentStorageClient.storeDocument(sampleSAR, byteArrayOutputStream))
      .thenReturn(postDocumentResponse)

    whenever(subjectAccessRequestService.findUnclaimed()).thenReturn(listOf(sampleSAR))

    subjectAccessRequestWorkerService.pollForRequests()

    verify(subjectAccessRequestService, times(1)).findUnclaimed()
    verify(subjectAccessRequestService, times(1)).updateStatus(
      UUID.fromString("11111111-1111-1111-1111-111111111111"),
      Status.Completed,
    )
  }

  @Test
  fun `startPolling doesn't call complete if claim patch fails`() = runTest {
    whenever(subjectAccessRequestService.findUnclaimed()).thenReturn(listOf(sampleSAR))
    whenever(subjectAccessRequestService.updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(any())).thenThrow(
      RuntimeException("Database error"),
    )

    subjectAccessRequestWorkerService.pollForRequests()

    verify(subjectAccessRequestService, times(1)).findUnclaimed()
    verify(subjectAccessRequestService, times(0)).updateStatus(any(), eq(Status.Completed))
  }

  @Nested
  inner class CreateSubjectAccessRequestReport {

    @Test
    fun `doReport calls GetSubjectAccessRequestDataService execute`() {
      whenever(serviceConfigurationService.getSelectedServices(any())).thenReturn(selectedDpsServices)
      whenever(
        getSubjectAccessRequestDataService.requestDataFromServices(
          selectedDpsServices,
          null,
          "1",
          dateFromFormatted,
          dateToFormatted,
          sampleSAR,
        ),
      )
        .thenReturn(dpsServicesList)
      whenever(generatePdfService.getPdfWriter(byteArrayOutputStream)).thenReturn(pdfWriter)
      whenever(probationApiClient.getOffenderName(sampleSAR, "1")).thenReturn("TEST, Name")
      whenever(
        generatePdfService.execute(
          services = dpsServicesList,
          subjectName = "TEST, Name",
          sar = sampleSAR,
        ),
      )
        .thenReturn(byteArrayOutputStream)
      whenever(documentStorageClient.storeDocument(sampleSAR, byteArrayOutputStream))
        .thenReturn(postDocumentResponse)

      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)

      verify(getSubjectAccessRequestDataService, times(1)).requestDataFromServices(
        any(),
        eq(null),
        any(),
        any(),
        any(),
        any(),
      )
    }

    @Test
    fun `doReport calls GeneratePdfService execute`() {
      whenever(serviceConfigurationService.getSelectedServices(any())).thenReturn(selectedDpsServices)
      whenever(
        getSubjectAccessRequestDataService.requestDataFromServices(
          selectedDpsServices,
          null,
          "1",
          dateFromFormatted,
          dateToFormatted,
          sampleSAR,
        ),
      )
        .thenReturn(dpsServicesList)
      whenever(generatePdfService.getPdfWriter(byteArrayOutputStream)).thenReturn(pdfWriter)
      whenever(probationApiClient.getOffenderName(sampleSAR, "1"))
        .thenReturn("TEST, Name")
      whenever(
        generatePdfService.execute(
          services = dpsServicesList,
          subjectName = "TEST, Name",
          sar = sampleSAR,
        ),
      ).thenReturn(byteArrayOutputStream)
      whenever(
        documentStorageClient.storeDocument(
          sampleSAR,
          byteArrayOutputStream,
        ),
      ).thenReturn(postDocumentResponse)

      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)

      verify(generatePdfService, times(1)).execute(
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
      )
    }

    @Test
    fun `doReport calls storeDocument`() = runTest {
      whenever(serviceConfigurationService.getSelectedServices(any())).thenReturn(selectedDpsServices)
      whenever(
        getSubjectAccessRequestDataService.requestDataFromServices(
          selectedDpsServices,
          null,
          "1",
          dateFromFormatted,
          dateToFormatted,
          sampleSAR,
        ),
      )
        .thenReturn(dpsServicesList)
      whenever(generatePdfService.getPdfWriter(byteArrayOutputStream))
        .thenReturn(pdfWriter)
      whenever(probationApiClient.getOffenderName(sampleSAR, "1"))
        .thenReturn("TEST, Name")
      whenever(
        generatePdfService.execute(
          services = dpsServicesList,
          subjectName = "TEST, Name",
          sar = sampleSAR,
        ),
      )
        .thenReturn(byteArrayOutputStream)
      whenever(documentStorageClient.storeDocument(sampleSAR, byteArrayOutputStream))
        .thenReturn(postDocumentResponse)

      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)

      verify(documentStorageClient, times(1)).storeDocument(
        sampleSAR,
        byteArrayOutputStream,
      )
    }
  }
}
