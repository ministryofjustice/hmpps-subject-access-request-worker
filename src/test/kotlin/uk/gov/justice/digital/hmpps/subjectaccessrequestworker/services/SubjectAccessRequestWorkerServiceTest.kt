package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfWriter
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
        nomisId = null,
        ndeliusCaseReferenceId = "1",
        sarCaseReferenceNumber = "1234abc",
        subjectName = "TEST, Name",
        dateFrom = dateFromFormatted,
        dateTo = dateToFormatted,
        subjectAccessRequest = sampleSAR,
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
    fun `doReport calls getSubjectAccessRequestDataService with chosenSar details`() {
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
      ).thenReturn(dpsServicesList)
      whenever(generatePdfService.getPdfWriter(byteArrayOutputStream)).thenReturn(pdfWriter)
      whenever(probationApiClient.getOffenderName(sampleSAR, "1")).thenReturn("TEST, Name")
      whenever(
        generatePdfService.execute(
          services = dpsServicesList,
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "TEST, Name",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
          subjectAccessRequest = sampleSAR,
        ),
      ).thenReturn(byteArrayOutputStream)
      whenever(documentStorageClient.storeDocument(sampleSAR, byteArrayOutputStream)).thenReturn(postDocumentResponse)

      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)

      verify(getSubjectAccessRequestDataService, times(1)).requestDataFromServices(
        services = selectedDpsServices,
        null,
        "1",
        dateFromFormatted,
        dateToFormatted,
        sampleSAR,
      )
    }

    @Test
    fun `createSubjectAccessRequestReport throws exception if an error occurs during attempt to retrieve upstream API info`() {
      val selectedServicesList = mutableListOf(
        DpsService(
          name = "test-dps-service-2",
          businessName = "Test DPS Service 2",
          orderPosition = 1,
          url = null,
        ),
      )

      whenever(serviceConfigurationService.getSelectedServices(any()))
        .thenReturn(selectedServicesList)

      whenever(
        getSubjectAccessRequestDataService.requestDataFromServices(
          services = selectedServicesList,
          null,
          "1",
          dateFromFormatted,
          dateToFormatted,
          sampleSAR,
        ),
      ).thenThrow(RuntimeException())

      val exception = assertThrows<RuntimeException> {
        subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)
      }

      assertThat(exception.message).isNull()
    }

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
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "TEST, Name",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
          subjectAccessRequest = sampleSAR,
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
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "TEST, Name",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
          subjectAccessRequest = sampleSAR,
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
        anyOrNull(),
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
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "TEST, Name",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
          subjectAccessRequest = sampleSAR,
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

  @Nested
  inner class GetServiceDetails {
    private val sampleSAR = SubjectAccessRequest(
      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      status = Status.Pending,
      dateFrom = dateFromFormatted,
      dateTo = dateToFormatted,
      sarCaseReferenceNumber = "1234abc",
      services = "test-dps-service-2, https://test-dps-service-2.prison.service.justice.gov.uk,test-dps-service-1, https://test-dps-service-1.prison.service.justice.gov.uk",
      nomisId = null,
      ndeliusCaseReferenceId = "1",
      requestedBy = "aName",
      requestDateTime = LocalDateTime.now(),
      claimAttempts = 0,
    )

    private val selectedDpsServices = mutableListOf(
      DpsService(
        name = "test-dps-service-2",
        url = "https://test-dps-service-2.prison.service.justice.gov.uk",
        businessName = null,
        orderPosition = null,
      ),
      DpsService(
        name = "test-dps-service-1",
        url = "https://test-dps-service-1.prison.service.justice.gov.uk",
        businessName = null,
        orderPosition = null,
      ),
    )

//    private val serviceConfigObject = ServiceConfig(
//      dpsServices =
//      mutableListOf(
//        DpsService(name = "test-dps-service-2", url = null, businessName = "Test DPS Service 2", orderPosition = 2),
//        DpsService(name = "test-dps-service-1", url = null, businessName = "Test DPS Service 1", orderPosition = 1),
//      ),
//    )

//    @Test
//    fun `getServiceDetails returns a list of DPS Service objects`() = runTest {
//      whenever(serviceConfigurationService.getSelectedServices(any())).thenReturn(selectedDpsServices)
//
//      whenever(
//        configOrderHelper.getDpsServices(
//          mapOf(
//            "test-dps-service-2" to "https://test-dps-service-2.prison.service.justice.gov.uk",
//            "test-dps-service-1" to "https://test-dps-service-1.prison.service.justice.gov.uk",
//          ),
//        ),
//      ).thenReturn(selectedDpsServices)
//      whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
//
//      val detailedSelectedServices = subjectAccessRequestWorkerService.getServiceDetails(sampleSAR)
//
//      assertThat(detailedSelectedServices).isInstanceOf(List::class.java)
//      assertThat(detailedSelectedServices[0]).isInstanceOf(DpsService::class.java)
//    }

//    @Test
//    fun `getServiceDetails extracts the correct details for the given SAR`() = runTest {
//      whenever(
//        configOrderHelper.getDpsServices(
//          mapOf(
//            "test-dps-service-2" to "https://test-dps-service-2.prison.service.justice.gov.uk",
//            "test-dps-service-1" to "https://test-dps-service-1.prison.service.justice.gov.uk",
//          ),
//        ),
//      ).thenReturn(selectedDpsServices)
//      whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
//
//      val detailedSelectedServices = subjectAccessRequestWorkerService.getServiceDetails(sampleSAR)
//
//      assertThat(detailedSelectedServices[0].name).isEqualTo("test-dps-service-2")
//      assertThat(detailedSelectedServices[0].businessName).isEqualTo("Test DPS Service 2")
//      assertThat(detailedSelectedServices[0].url).isEqualTo("https://test-dps-service-2.prison.service.justice.gov.uk")
//      assertThat(detailedSelectedServices[0].orderPosition).isEqualTo(2)
//      assertThat(detailedSelectedServices.size).isEqualTo(2)
//    }
  }
}
