package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfWriter
import com.microsoft.applicationinsights.TelemetryClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.mockkStatic
import io.sentry.Sentry
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatusCode
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.DocumentStorageGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.PrisonApiGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.ProbationApiGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.ServiceConfig
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils.ConfigOrderHelper
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@ActiveProfiles("test")
class SubjectAccessRequestWorkerServiceTest : IntegrationTestBase() {
  private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private val dateFrom = "02/01/2023"
  private val dateFromFormatted = LocalDate.parse(dateFrom, formatter)
  private val dateTo = "02/01/2024"
  private val dateToFormatted = LocalDate.parse(dateTo, formatter)
  private val requestTime = LocalDateTime.now()
  private val documentGateway: DocumentStorageGateway = Mockito.mock(DocumentStorageGateway::class.java)
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
  val selectedDpsServices =
    mutableListOf(
      DpsService(name = "fake-hmpps-prisoner-search", url = "https://fake-prisoner-search.prison.service.justice.gov.uk", businessName = null, orderPosition = null),
      DpsService(name = "fake-hmpps-prisoner-search-indexer", url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", businessName = null, orderPosition = null),
    )
  val serviceConfigObject = ServiceConfig(
    dpsServices =
    mutableListOf(
      DpsService(name = "fake-hmpps-prisoner-search", url = null, businessName = "HMPPS Prisoner Search", orderPosition = 1),
      DpsService(name = "fake-hmpps-prisoner-search-indexer", url = null, businessName = "HMPPS Prisoner Indexer", orderPosition = 2),
    ),
  )
  private val mockSarGateway = Mockito.mock(SubjectAccessRequestGateway::class.java)
  private val mockGetSubjectAccessRequestDataService = Mockito.mock(GetSubjectAccessRequestDataService::class.java)
  private val mockPrisonApiGateway = Mockito.mock(PrisonApiGateway::class.java)
  private val mockProbationApiGateway = Mockito.mock(ProbationApiGateway::class.java)
  private val mockGeneratePdfService = Mockito.mock(GeneratePdfService::class.java)
  private val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
  private val telemetryClient = Mockito.mock(TelemetryClient::class.java)
  private val configOrderHelper = Mockito.mock(ConfigOrderHelper::class.java)
  private val mockWriter = Mockito.mock(PdfWriter::class.java)
  private val mockWebClient = Mockito.mock(WebClient::class.java)

  val subjectAccessRequestWorkerService = SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, mockPrisonApiGateway, mockProbationApiGateway, configOrderHelper, "http://localhost:8080", telemetryClient)

  @Test
  fun `pollForNewSubjectAccessRequests returns single SubjectAccessRequest`() = runTest {
    val requestHeadersUriSpecMock = Mockito.mock(WebClient.RequestHeadersUriSpec::class.java)
    val requestHeadersSpecMock = Mockito.mock(WebClient.RequestHeadersSpec::class.java)
    val responseSpecMock = Mockito.mock(WebClient.ResponseSpec::class.java)
    Mockito.`when`(mockWebClient.get())
      .thenReturn(requestHeadersUriSpecMock)
    Mockito.`when`(requestHeadersUriSpecMock.uri("/api/subjectAccessRequests?unclaimed=true"))
      .thenReturn(requestHeadersSpecMock)
    Mockito.`when`(requestHeadersSpecMock.retrieve())
      .thenReturn(responseSpecMock)
    Mockito.`when`(responseSpecMock.bodyToMono(Array<SubjectAccessRequest>::class.java))
      .thenReturn(Mono.just(arrayOf(sampleSAR)))
    Mockito.`when`(mockSarGateway.getUnclaimed(mockWebClient)).thenReturn(arrayOf(sampleSAR))

    Mockito.`when`(mockSarGateway.getUnclaimed(mockWebClient)).thenReturn(arrayOf(sampleSAR))

    val result = SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, mockPrisonApiGateway, mockProbationApiGateway, configOrderHelper, "http://localhost:8080", telemetryClient)
      .pollForNewSubjectAccessRequests(mockWebClient)

    val expected: SubjectAccessRequest = sampleSAR
    Assertions.assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `doPoll polls for unclaimed SAR`() = runTest {
    Mockito.`when`(mockSarGateway.getClient("http://localhost:8080")).thenReturn(mockWebClient)
    Mockito.`when`(mockSarGateway.getUnclaimed(mockWebClient)).thenReturn(arrayOf(sampleSAR))

    SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, mockPrisonApiGateway, mockProbationApiGateway, configOrderHelper, "http://localhost:8080", telemetryClient).doPoll()

    verify(mockSarGateway, Mockito.times(1)).getUnclaimed(mockWebClient)
  }

  @Test
  fun `startPolling calls claim and complete on happy path`() = runTest {
    Mockito.`when`(
      configOrderHelper.getDpsServices(
        mapOf(
          "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
          "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
        ),
      ),
    ).thenReturn(selectedDpsServices)
    Mockito.`when`(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
    Mockito.`when`(mockSarGateway.getClient("http://localhost:8080")).thenReturn(mockWebClient)
    Mockito.`when`(mockSarGateway.getUnclaimed(mockWebClient)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(mockSarGateway.claim(mockWebClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(200))
    Mockito.`when`(mockSarGateway.complete(mockWebClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(200))
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGeneratePdfService.createPdfStream())
      .thenReturn(mockStream)
    Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream))
      .thenReturn(mockWriter)
    Mockito.`when`(
      mockGeneratePdfService.execute(
        content = linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
        nomisId = null,
        ndeliusCaseReferenceId = "1",
        subjectName = "testName",
        dateTo = dateToFormatted,
        dateFrom = dateFromFormatted,
        sarCaseReferenceNumber = "1234abc",
      ),
    )
      .thenReturn(mockStream)
    Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream))
      .thenReturn("")

    SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, mockPrisonApiGateway, mockProbationApiGateway, configOrderHelper, "http://localhost:8080", telemetryClient).doPoll()

    verify(mockSarGateway, Mockito.times(1)).claim(mockWebClient, sampleSAR)
    verify(mockSarGateway, Mockito.times(1)).complete(mockWebClient, sampleSAR)
  }

  @Test
  fun `startPolling doesn't call complete if claim patch fails`() = runTest {
    Mockito.`when`(mockSarGateway.getClient("http://localhost:8080")).thenReturn(mockWebClient)
    Mockito.`when`(mockSarGateway.getUnclaimed(mockWebClient)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(mockSarGateway.claim(mockWebClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(400))

    SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, mockPrisonApiGateway, mockProbationApiGateway, configOrderHelper, "http://localhost:8080", telemetryClient).doPoll()

    verify(mockSarGateway, Mockito.times(1)).claim(mockWebClient, sampleSAR)
    verify(mockSarGateway, Mockito.times(0)).complete(mockWebClient, sampleSAR)
  }

  @Nested
  inner class DoReport {
    @Test
    fun `doReport calls getSubjectAccessRequestDataService with chosenSar details`() {
      Mockito.`when`(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      Mockito.`when`(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
        .thenReturn(linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
      Mockito.`when`(mockGeneratePdfService.createPdfStream())
        .thenReturn(mockStream)
      Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream))
        .thenReturn(mockWriter)
      Mockito.`when`(
        mockGeneratePdfService.execute(
          content = linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "testName",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
        ),
      )
        .thenReturn(mockStream)

      subjectAccessRequestWorkerService.doReport(sampleSAR)

      verify(mockGetSubjectAccessRequestDataService, Mockito.times(1)).execute(services = selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted)
    }

    @Test
    fun `doReport throws exception if an error occurs during attempt to retrieve upstream API info`() {
      Mockito.`when`(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      Mockito.`when`(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(services = selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
        .thenThrow(RuntimeException())
      Mockito.`when`(configOrderHelper.extractServicesConfig(any())).thenReturn(
        ServiceConfig(mutableListOf(DpsService(name = "test-dps-service-2", businessName = "Test DPS Service 2", orderPosition = 1, url = null))),
      )

      val exception = shouldThrow<RuntimeException> {
        subjectAccessRequestWorkerService.doReport(sampleSAR)
      }

      exception.message.shouldBe(null)
    }

    @Test
    fun `doReport calls GetSubjectAccessRequestDataService execute`() {
      Mockito.`when`(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      Mockito.`when`(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
        .thenReturn(linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
      Mockito.`when`(mockGeneratePdfService.createPdfStream())
        .thenReturn(mockStream)
      Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream))
        .thenReturn(mockWriter)
      Mockito.`when`(
        mockGeneratePdfService.execute(
          content = linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "testName",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
        ),
      )
        .thenReturn(mockStream)
      Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream))
        .thenReturn("")

      subjectAccessRequestWorkerService.doReport(sampleSAR)

      verify(mockGetSubjectAccessRequestDataService, Mockito.times(1)).execute(any(), eq(null), any(), any(), any())
    }

    @Test
    fun `doReport calls GeneratePdfService execute`() {
      Mockito.`when`(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      Mockito.`when`(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
        .thenReturn(linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
      Mockito.`when`(mockGeneratePdfService.createPdfStream()).thenReturn(mockStream)
      Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream)).thenReturn(mockWriter)
      Mockito.`when`(
        mockGeneratePdfService.execute(
          content = linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "testName",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
        ),
      ).thenReturn(mockStream)
      Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream)).thenReturn("")

      subjectAccessRequestWorkerService.doReport(sampleSAR)

      verify(mockGeneratePdfService, Mockito.times(1)).execute(any(), eq(null), any(), any(), any(), any(), any())
    }

    @Test
    fun `doReport calls storeDocument`() = runTest {
      Mockito.`when`(
        configOrderHelper.getDpsServices(
          mapOf(
            "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
            "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      Mockito.`when`(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)
      Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
        .thenReturn(linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
      Mockito.`when`(mockGeneratePdfService.createPdfStream())
        .thenReturn(mockStream)
      Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream))
        .thenReturn(mockWriter)
      Mockito.`when`(
        mockGeneratePdfService.execute(
          content = linkedMapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
          nomisId = null,
          ndeliusCaseReferenceId = "1",
          subjectName = "testName",
          dateTo = dateToFormatted,
          dateFrom = dateFromFormatted,
          sarCaseReferenceNumber = "1234abc",
        ),
      )
        .thenReturn(mockStream)
      Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream))
        .thenReturn("")

      SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, mockPrisonApiGateway, mockProbationApiGateway, configOrderHelper, "http://localhost:8080", telemetryClient).doReport(sampleSAR)

      verify(documentGateway, Mockito.times(1)).storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream)
    }
  }

  @Test
  fun `doPoll exceptions are captured by sentry`() = runTest {
    mockkStatic(Sentry::class)
    Mockito.`when`(mockSarGateway.getClient("http://localhost:8080")).thenReturn(mockWebClient)
    Mockito.`when`(mockSarGateway.getUnclaimed(mockWebClient))
      .thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(mockSarGateway.claim(any(), any()))
      .thenThrow(RuntimeException())

    SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, mockPrisonApiGateway, mockProbationApiGateway, configOrderHelper, "http://localhost:8080", telemetryClient).doPoll()

    io.mockk.verify(exactly = 1) {
      Sentry.captureException(
        any(),
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
      DpsService(name = "test-dps-service-2", url = "https://test-dps-service-2.prison.service.justice.gov.uk", businessName = null, orderPosition = null),
      DpsService(name = "test-dps-service-1", url = "https://test-dps-service-1.prison.service.justice.gov.uk", businessName = null, orderPosition = null),
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
      Mockito.`when`(
        configOrderHelper.getDpsServices(
          mapOf(
            "test-dps-service-2" to "https://test-dps-service-2.prison.service.justice.gov.uk",
            "test-dps-service-1" to "https://test-dps-service-1.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      Mockito.`when`(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)

      val detailedSelectedServices = subjectAccessRequestWorkerService.getServiceDetails(sampleSAR)

      Assertions.assertThat(detailedSelectedServices).isInstanceOf(List::class.java)
      Assertions.assertThat(detailedSelectedServices[0]).isInstanceOf(DpsService::class.java)
    }

    @Test
    fun `getServiceDetails extracts the correct details for the given SAR`() = runTest {
      Mockito.`when`(
        configOrderHelper.getDpsServices(
          mapOf(
            "test-dps-service-2" to "https://test-dps-service-2.prison.service.justice.gov.uk",
            "test-dps-service-1" to "https://test-dps-service-1.prison.service.justice.gov.uk",
          ),
        ),
      ).thenReturn(selectedDpsServices)
      Mockito.`when`(configOrderHelper.extractServicesConfig("servicesConfig.yaml")).thenReturn(serviceConfigObject)

      val detailedSelectedServices = subjectAccessRequestWorkerService.getServiceDetails(sampleSAR)

      Assertions.assertThat(detailedSelectedServices[0].name).isEqualTo("test-dps-service-2")
      Assertions.assertThat(detailedSelectedServices[0].businessName).isEqualTo("Test DPS Service 2")
      Assertions.assertThat(detailedSelectedServices[0].url).isEqualTo("https://test-dps-service-2.prison.service.justice.gov.uk")
      Assertions.assertThat(detailedSelectedServices[0].orderPosition).isEqualTo(2)
      Assertions.assertThat(detailedSelectedServices.size).isEqualTo(2)
    }
  }
}
