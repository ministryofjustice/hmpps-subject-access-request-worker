package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfWriter
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DocumentStorageClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.PrisonApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.ProbationApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfig
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.ConfigOrderHelper
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class SubjectAccessRequestWorkerServiceTest : IntegrationTestBase() {
  private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private val dateFrom = "02/01/2023"
  private val dateFromFormatted = LocalDate.parse(dateFrom, formatter)
  private val dateTo = "02/01/2024"
  private val dateToFormatted = LocalDate.parse(dateTo, formatter)
  private val requestTime = LocalDateTime.now()
  private val documentStorageClient: DocumentStorageClient = mock()
  private val sampleSAR = SubjectAccessRequest(
    id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
    status = Status.Pending,
    dateFrom = dateFromFormatted,
    dateTo = dateToFormatted,
    sarCaseReferenceNumber = "1234abc",
    services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk,fake-hmpps-prisoner-search-indexer, https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
    nomisId = null,
    ndeliusCaseReferenceId = "1",
    requestedBy = "aName",
    requestDateTime = requestTime,
    claimAttempts = 0,
  )
  private val mockDpsServices = listOf(DpsService(), DpsService())

  val selectedDpsServices =
    mutableListOf(
      DpsService(
        name = "fake-hmpps-prisoner-search",
        url = "https://fake-prisoner-search.prison.service.justice.gov.uk",
        businessName = null,
        orderPosition = null,
      ),
      DpsService(
        name = "fake-hmpps-prisoner-search-indexer",
        url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
        businessName = null,
        orderPosition = null,
      ),
    )
  val serviceConfigObject = ServiceConfig(
    dpsServices =
    mutableListOf(
      DpsService(
        name = "fake-hmpps-prisoner-search",
        url = null,
        businessName = "HMPPS Prisoner Search",
        orderPosition = 1,
      ),
      DpsService(
        name = "fake-hmpps-prisoner-search-indexer",
        url = null,
        businessName = "HMPPS Prisoner Indexer",
        orderPosition = 2,
      ),
    ),
  )
  private val mockSarGateway: SubjectAccessRequestGateway = mock()
  private val mockGetSubjectAccessRequestDataService: GetSubjectAccessRequestDataService = mock()
  private val prisonApiClient: PrisonApiClient = mock()
  private val probationApiClient: ProbationApiClient = mock()
  private val generatePdfService: GeneratePdfService = mock()
  private val mockStream: ByteArrayOutputStream = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val configOrderHelper: ConfigOrderHelper = mock()
  private val mockWriter: PdfWriter = mock()
  private val mockWebClient: WebClient = mock()

  val subjectAccessRequestWorkerService = SubjectAccessRequestWorkerService(
    mockSarGateway,
    mockGetSubjectAccessRequestDataService,
    documentStorageClient,
    generatePdfService,
    prisonApiClient,
    probationApiClient,
    configOrderHelper,
    "http://localhost:8080",
    telemetryClient,
  )

  private val postDocumentResponse = DocumentStorageClient.PostDocumentResponse(documentUuid = sampleSAR.id.toString())

  @Test
  fun `pollForNewSubjectAccessRequests returns single SubjectAccessRequest`() = runTest {
    whenever(mockSarGateway.getUnclaimed(mockWebClient)).thenReturn(arrayOf(sampleSAR))

    val result = subjectAccessRequestWorkerService
      .pollForNewSubjectAccessRequests(mockWebClient)

    val expected: SubjectAccessRequest = sampleSAR
    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `doPoll polls for unclaimed SAR`() = runTest {
    whenever(mockSarGateway.getClient("http://localhost:8080")).thenReturn(mockWebClient)
    whenever(mockSarGateway.getUnclaimed(mockWebClient)).thenReturn(arrayOf(sampleSAR))

    subjectAccessRequestWorkerService.doPoll()

    verify(mockSarGateway, times(1)).getUnclaimed(mockWebClient)
  }

  @Test
  fun `startPolling calls claim and complete on happy path`() = runTest {
    whenever(
      configOrderHelper.getDpsServices(
        mapOf(
          "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
          "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
        ),
      ),
    ).thenReturn(selectedDpsServices)
    whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
    whenever(mockSarGateway.getClient("http://localhost:8080")).thenReturn(mockWebClient)
    whenever(mockSarGateway.getUnclaimed(mockWebClient)).thenReturn(arrayOf(sampleSAR))
    whenever(
      mockGetSubjectAccessRequestDataService.requestDataFromServices(
        selectedDpsServices,
        null,
        "1",
        dateFromFormatted,
        dateToFormatted,
        sampleSAR,
      ),
    )
      .thenReturn(mockDpsServices)
    whenever(generatePdfService.createPdfStream())
      .thenReturn(mockStream)
    whenever(generatePdfService.getPdfWriter(mockStream))
      .thenReturn(mockWriter)
    whenever(probationApiClient.getOffenderName(sampleSAR, "1"))
      .thenReturn("TEST, Name")
    whenever(
      generatePdfService.execute(
        services = mockDpsServices,
        nomisId = null,
        ndeliusCaseReferenceId = "1",
        sarCaseReferenceNumber = "1234abc",
        subjectName = "TEST, Name",
        dateFrom = dateFromFormatted,
        dateTo = dateToFormatted,
        subjectAccessRequest = sampleSAR,
      ),
    ).thenReturn(mockStream)

    whenever(documentStorageClient.storeDocument(sampleSAR, mockStream))
      .thenReturn(postDocumentResponse)

    subjectAccessRequestWorkerService.doPoll()

    verify(mockSarGateway, times(1)).claim(mockWebClient, sampleSAR)
    verify(mockSarGateway, times(1)).complete(mockWebClient, sampleSAR)
  }

  @Test
  fun `startPolling doesn't call complete if claim patch fails`() = runTest {
    whenever(mockSarGateway.getClient("http://localhost:8080")).thenReturn(mockWebClient)
    whenever(mockSarGateway.getUnclaimed(mockWebClient)).thenReturn(arrayOf(sampleSAR))
    whenever(mockSarGateway.claim(mockWebClient, sampleSAR)).thenThrow(SubjectAccessRequestException("failed to claim SAR"))

    subjectAccessRequestWorkerService.doPoll()

    verify(mockSarGateway, times(1)).claim(mockWebClient, sampleSAR)
    verify(mockSarGateway, times(0)).complete(mockWebClient, sampleSAR)
  }

  @Nested
  inner class DoReport {
    @Test
    fun `doReport calls getSubjectAccessRequestDataService with chosenSar details`() {
      whenever(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      whenever(
        mockGetSubjectAccessRequestDataService.requestDataFromServices(
          selectedDpsServices,
          null,
          "1",
          dateFromFormatted,
          dateToFormatted,
          sampleSAR,
        ),
      )
        .thenReturn(mockDpsServices)
      whenever(generatePdfService.createPdfStream())
        .thenReturn(mockStream)
      whenever(generatePdfService.getPdfWriter(mockStream))
        .thenReturn(mockWriter)
      whenever(probationApiClient.getOffenderName(sampleSAR, "1"))
        .thenReturn("TEST, Name")
      whenever(
        generatePdfService.execute(
          services = mockDpsServices,
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "TEST, Name",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
          subjectAccessRequest = sampleSAR,
        ),
      ).thenReturn(mockStream)
      whenever(documentStorageClient.storeDocument(sampleSAR, mockStream))
        .thenReturn(postDocumentResponse)

      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)

      verify(mockGetSubjectAccessRequestDataService, times(1)).requestDataFromServices(
        services = selectedDpsServices,
        null,
        "1",
        dateFromFormatted,
        dateToFormatted,
        sampleSAR,
      )
    }

    @Test
    fun `doReport throws exception if an error occurs during attempt to retrieve upstream API info`() {
      whenever(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      whenever(
        mockGetSubjectAccessRequestDataService.requestDataFromServices(
          services = selectedDpsServices,
          null,
          "1",
          dateFromFormatted,
          dateToFormatted,
          sampleSAR,
        ),
      )
        .thenThrow(RuntimeException())
      whenever(configOrderHelper.extractServicesConfig(any())).thenReturn(
        ServiceConfig(
          mutableListOf(
            DpsService(
              name = "test-dps-service-2",
              businessName = "Test DPS Service 2",
              orderPosition = 1,
              url = null,
            ),
          ),
        ),
      )

      val exception = assertThrows<RuntimeException> {
        subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)
      }

      assertThat(exception.message).isNull()
    }

    @Test
    fun `doReport calls GetSubjectAccessRequestDataService execute`() {
      whenever(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      whenever(
        mockGetSubjectAccessRequestDataService.requestDataFromServices(
          selectedDpsServices,
          null,
          "1",
          dateFromFormatted,
          dateToFormatted,
          sampleSAR,
        ),
      )
        .thenReturn(mockDpsServices)
      whenever(generatePdfService.createPdfStream())
        .thenReturn(mockStream)
      whenever(generatePdfService.getPdfWriter(mockStream))
        .thenReturn(mockWriter)
      whenever(probationApiClient.getOffenderName(sampleSAR, "1"))
        .thenReturn("TEST, Name")
      whenever(
        generatePdfService.execute(
          services = mockDpsServices,
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "TEST, Name",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
          subjectAccessRequest = sampleSAR,
        ),
      )
        .thenReturn(mockStream)
      whenever(documentStorageClient.storeDocument(sampleSAR, mockStream))
        .thenReturn(postDocumentResponse)

      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)

      verify(mockGetSubjectAccessRequestDataService, times(1)).requestDataFromServices(any(), eq(null), any(), any(), any(), any())
    }

    @Test
    fun `doReport calls GeneratePdfService execute`() {
      whenever(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      whenever(
        mockGetSubjectAccessRequestDataService.requestDataFromServices(
          selectedDpsServices,
          null,
          "1",
          dateFromFormatted,
          dateToFormatted,
          sampleSAR,
        ),
      )
        .thenReturn(mockDpsServices)
      whenever(generatePdfService.createPdfStream()).thenReturn(mockStream)
      whenever(generatePdfService.getPdfWriter(mockStream)).thenReturn(mockWriter)
      whenever(probationApiClient.getOffenderName(sampleSAR, "1"))
        .thenReturn("TEST, Name")
      whenever(
        generatePdfService.execute(
          services = mockDpsServices,
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "TEST, Name",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
          subjectAccessRequest = sampleSAR,
        ),
      ).thenReturn(mockStream)
      whenever(
        documentStorageClient.storeDocument(
          sampleSAR,
          mockStream,
        ),
      ).thenReturn(postDocumentResponse)

      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)

      verify(generatePdfService, times(1)).execute(any(), eq(null), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `doReport calls storeDocument`() = runTest {
      whenever(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      whenever(
        mockGetSubjectAccessRequestDataService.requestDataFromServices(
          selectedDpsServices,
          null,
          "1",
          dateFromFormatted,
          dateToFormatted,
          sampleSAR,
        ),
      )
        .thenReturn(mockDpsServices)
      whenever(generatePdfService.createPdfStream())
        .thenReturn(mockStream)
      whenever(generatePdfService.getPdfWriter(mockStream))
        .thenReturn(mockWriter)
      whenever(probationApiClient.getOffenderName(sampleSAR, "1"))
        .thenReturn("TEST, Name")
      whenever(
        generatePdfService.execute(
          services = mockDpsServices,
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "TEST, Name",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
          subjectAccessRequest = sampleSAR,
        ),
      )
        .thenReturn(mockStream)
      whenever(documentStorageClient.storeDocument(sampleSAR, mockStream))
        .thenReturn(postDocumentResponse)

      subjectAccessRequestWorkerService.createSubjectAccessRequestReport(sampleSAR)

      verify(documentStorageClient, times(1)).storeDocument(
        sampleSAR,
        mockStream,
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
      requestDateTime = requestTime,
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

    private val serviceConfigObject = ServiceConfig(
      dpsServices =
      mutableListOf(
        DpsService(name = "test-dps-service-2", url = null, businessName = "Test DPS Service 2", orderPosition = 2),
        DpsService(name = "test-dps-service-1", url = null, businessName = "Test DPS Service 1", orderPosition = 1),
      ),
    )

    @Test
    fun `getServiceDetails returns a list of DPS Service objects`() = runTest {
      whenever(
        configOrderHelper.getDpsServices(
          mapOf(
            "test-dps-service-2" to "https://test-dps-service-2.prison.service.justice.gov.uk",
            "test-dps-service-1" to "https://test-dps-service-1.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)

      val detailedSelectedServices = subjectAccessRequestWorkerService.getServiceDetails(sampleSAR)

      assertThat(detailedSelectedServices).isInstanceOf(List::class.java)
      assertThat(detailedSelectedServices[0]).isInstanceOf(DpsService::class.java)
    }

    @Test
    fun `getServiceDetails extracts the correct details for the given SAR`() = runTest {
      whenever(
        configOrderHelper.getDpsServices(
          mapOf(
            "test-dps-service-2" to "https://test-dps-service-2.prison.service.justice.gov.uk",
            "test-dps-service-1" to "https://test-dps-service-1.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      whenever(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)

      val detailedSelectedServices = subjectAccessRequestWorkerService.getServiceDetails(sampleSAR)

      assertThat(detailedSelectedServices[0].name).isEqualTo("test-dps-service-2")
      assertThat(detailedSelectedServices[0].businessName).isEqualTo("Test DPS Service 2")
      assertThat(detailedSelectedServices[0].url).isEqualTo("https://test-dps-service-2.prison.service.justice.gov.uk")
      assertThat(detailedSelectedServices[0].orderPosition).isEqualTo(2)
      assertThat(detailedSelectedServices.size).isEqualTo(2)
    }
  }
}
