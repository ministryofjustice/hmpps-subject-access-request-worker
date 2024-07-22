package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
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
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsServices
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
  private val selectedDpsServices = DpsServices(mutableListOf(DpsService(name = "fake-hmpps-prisoner-search", businessName = "Fake HMPPS Prisoner Search", orderPosition = 1, url = "https://fake-prisoner-search.prison.service.justice.gov.uk"), DpsService(name = "fake-hmpps-prisoner-search-indexer", businessName = "Fake HMPPS Prisoner Search Indexer", orderPosition = 2, url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk")))

  private val mockSarGateway = Mockito.mock(SubjectAccessRequestGateway::class.java)
  private val mockGetSubjectAccessRequestDataService = Mockito.mock(GetSubjectAccessRequestDataService::class.java)
  private val mockGeneratePdfService = Mockito.mock(GeneratePdfService::class.java)
  private val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
  private val telemetryClient = Mockito.mock(TelemetryClient::class.java)
  private val configOrderHelper = Mockito.mock(ConfigOrderHelper::class.java)

  val subjectAccessRequestWorkerService = SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, configOrderHelper, "http://localhost:8080", telemetryClient)

  @Test
  fun `pollForNewSubjectAccessRequests returns single SubjectAccessRequest`() = runTest {
    val mockClient = Mockito.mock(WebClient::class.java)
    val requestHeadersUriSpecMock = Mockito.mock(WebClient.RequestHeadersUriSpec::class.java)
    val requestHeadersSpecMock = Mockito.mock(WebClient.RequestHeadersSpec::class.java)
    val responseSpecMock = Mockito.mock(WebClient.ResponseSpec::class.java)
    Mockito.`when`(mockClient.get())
      .thenReturn(requestHeadersUriSpecMock)
    Mockito.`when`(requestHeadersUriSpecMock.uri("/api/subjectAccessRequests?unclaimed=true"))
      .thenReturn(requestHeadersSpecMock)
    Mockito.`when`(requestHeadersSpecMock.retrieve())
      .thenReturn(responseSpecMock)
    Mockito.`when`(responseSpecMock.bodyToMono(Array<SubjectAccessRequest>::class.java))
      .thenReturn(Mono.just(arrayOf(sampleSAR)))

    Mockito.`when`(mockSarGateway.getUnclaimed(mockClient)).thenReturn(arrayOf(sampleSAR))

    val result = SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, configOrderHelper, "http://localhost:8080", telemetryClient)
      .pollForNewSubjectAccessRequests(mockClient)

    val expected: SubjectAccessRequest = sampleSAR
    Assertions.assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `doPoll calls pollForNewSubjectAccessRequests`() = runTest {
    val mockClient = Mockito.mock(WebClient::class.java)
    val websarGatewayMock = Mockito.mock(SubjectAccessRequestGateway::class.java)
    Mockito.`when`(websarGatewayMock.getClient("http://localhost:8080")).thenReturn(mockClient)
    Mockito.`when`(websarGatewayMock.getUnclaimed(mockClient)).thenReturn(arrayOf(sampleSAR))

    SubjectAccessRequestWorkerService(websarGatewayMock, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, configOrderHelper, "http://localhost:8080", telemetryClient).doPoll()

    verify(websarGatewayMock, Mockito.times(1)).getUnclaimed(mockClient)
  }

  @Test
  fun `startPolling calls claim and complete on happy path`() = runTest {
    val mockClient = Mockito.mock(WebClient::class.java)
    val webSarGatewayMock = Mockito.mock(SubjectAccessRequestGateway::class.java)
    Mockito.`when`(webSarGatewayMock.getClient("http://localhost:8080")).thenReturn(mockClient)
    Mockito.`when`(webSarGatewayMock.getUnclaimed(mockClient)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(webSarGatewayMock.claim(mockClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(200))
    Mockito.`when`(webSarGatewayMock.complete(mockClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(200))

    val mockWriter = Mockito.mock(PdfWriter::class.java)
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGeneratePdfService.createPdfStream())
      .thenReturn(mockStream)
    Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream))
      .thenReturn(mockWriter)
    Mockito.`when`(
      mockGeneratePdfService.execute(
        content = mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
        nomisId = null,
        ndeliusCaseReferenceId = "1",
        dateTo = dateToFormatted,
        dateFrom = dateFromFormatted,
        sarCaseReferenceNumber = "1234abc",
      ),
    )
      .thenReturn(mockStream)
    Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream))
      .thenReturn("")
    SubjectAccessRequestWorkerService(webSarGatewayMock, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, configOrderHelper, "http://localhost:8080", telemetryClient).doPoll()
    verify(webSarGatewayMock, Mockito.times(1)).claim(mockClient, sampleSAR)
    verify(webSarGatewayMock, Mockito.times(1)).complete(mockClient, sampleSAR)
  }

  @Test
  fun `startPolling doesn't call complete if claim patch fails`() = runTest {
    val mockClient = Mockito.mock(WebClient::class.java)
    val websarGatewayMock = Mockito.mock(SubjectAccessRequestGateway::class.java)
    Mockito.`when`(websarGatewayMock.getClient("http://localhost:8080")).thenReturn(mockClient)
    Mockito.`when`(websarGatewayMock.getUnclaimed(mockClient)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(websarGatewayMock.claim(mockClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(400))
    SubjectAccessRequestWorkerService(websarGatewayMock, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, configOrderHelper, "http://localhost:8080", telemetryClient).doPoll()
    verify(websarGatewayMock, Mockito.times(1)).claim(mockClient, sampleSAR)
    verify(websarGatewayMock, Mockito.times(0)).complete(mockClient, sampleSAR)
  }

  @Test
  fun `doReport calls getSubjectAccessRequestDataService with chosenSar details`() {
    val mockWriter = Mockito.mock(PdfWriter::class.java)

    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGeneratePdfService.createPdfStream())
      .thenReturn(mockStream)
    Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream))
      .thenReturn(mockWriter)
    Mockito.`when`(
      mockGeneratePdfService.execute(
        content = mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
        nomisId = null,
        ndeliusCaseReferenceId = "1",
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
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(services = selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
      .thenThrow(RuntimeException())
    Mockito.`when`(configOrderHelper.extractServicesConfig(any())).thenReturn(
      DpsServices(mutableListOf(DpsService(name = "test-dps-service-2", businessName = "Test DPS Service 2", orderPosition = 1, url = null)))
    )

    val exception = shouldThrow<RuntimeException> {
      subjectAccessRequestWorkerService.doReport(sampleSAR)
    }
    exception.message.shouldBe(null)
  }

  @Test
  fun `doReport calls GetSubjectAccessRequestDataService execute`() {
//    Mockito.`when`(configOrderHelper.extractServicesConfig(any())).thenReturn(
//      DpsServices(mutableListOf(DpsService(name = "test-dps-service-2", businessName = "Test DPS Service 2", orderPosition = 1, url = "something.com")))
//    )
    val mockWriter = Mockito.mock(PdfWriter::class.java)
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGeneratePdfService.createPdfStream())
      .thenReturn(mockStream)
    Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream))
      .thenReturn(mockWriter)
    Mockito.`when`(
      mockGeneratePdfService.execute(
        content = mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
        nomisId = null,
        ndeliusCaseReferenceId = "1",
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

//    mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk", "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk")

    val selectedDpsServices = DpsServices(
      mutableListOf(
        DpsService(name = "fake-hmpps-prisoner-search-indexer", businessName = null, orderPosition = null, url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"),
        DpsService(name = "fake-hmpps-prisoner-search", businessName = null, orderPosition = null, url = "https://fake-prisoner-search.prison.service.justice.gov.uk")
      )
    )

    val mockWriter = Mockito.mock(PdfWriter::class.java)

//    Mockito.`when`(configOrderHelper.extractServicesConfig(any())).thenReturn(
//      DpsServices(mutableListOf(DpsService(name = "test-dps-service-2", businessName = "Test DPS Service 2", orderPosition = 1, url = null)))
//    )


//    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
//      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
//
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))


    Mockito.`when`(mockGeneratePdfService.createPdfStream())
      .thenReturn(mockStream)
    Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream))
      .thenReturn(mockWriter)
    Mockito.`when`(
      mockGeneratePdfService.execute(
        content = mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
        nomisId = null,
        ndeliusCaseReferenceId = "1",
        dateTo = dateToFormatted,
        dateFrom = dateFromFormatted,
        sarCaseReferenceNumber = "1234abc",
      ),
    )
      .thenReturn(mockStream)
    Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream))
      .thenReturn("")

    subjectAccessRequestWorkerService.doReport(sampleSAR)

    verify(mockGeneratePdfService, Mockito.times(1)).execute(any(), any(), any(), any(), any(), any(), any())
  }

  @Test
  fun `doReport calls storeSubjectAccessRequestDocument`() = runTest {
    val mockWriter = Mockito.mock(PdfWriter::class.java)
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(selectedDpsServices, null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGeneratePdfService.createPdfStream())
      .thenReturn(mockStream)
    Mockito.`when`(mockGeneratePdfService.getPdfWriter(mockStream))
      .thenReturn(mockWriter)
    Mockito.`when`(
      mockGeneratePdfService.execute(
        content = mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())),
        nomisId = null,
        ndeliusCaseReferenceId = "1",
        dateTo = dateToFormatted,
        dateFrom = dateFromFormatted,
        sarCaseReferenceNumber = "1234abc",
      ),
    )
      .thenReturn(mockStream)
    Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream))
      .thenReturn("")
    SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, configOrderHelper, "http://localhost:8080", telemetryClient).doReport(sampleSAR)
    verify(documentGateway, Mockito.times(1)).storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream)
  }

  @Test
  fun `doPoll exceptions are captured by sentry`() = runTest {
    mockkStatic(Sentry::class)
    val mockClient = Mockito.mock(WebClient::class.java)
    val websarGatewayMock = Mockito.mock(SubjectAccessRequestGateway::class.java)
    Mockito.`when`(websarGatewayMock.getClient("http://localhost:8080")).thenReturn(mockClient)
    Mockito.`when`(websarGatewayMock.getUnclaimed(mockClient))
      .thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(websarGatewayMock.claim(any(), any()))
      .thenThrow(RuntimeException())

    SubjectAccessRequestWorkerService(websarGatewayMock, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, configOrderHelper, "http://localhost:8080", telemetryClient).doPoll()

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
    @Test
    fun `getServiceDetails returns a list of ServiceDetails objects`() = runTest {
      val orderedServiceDetailsList = subjectAccessRequestWorkerService.getServiceDetails(sampleSAR)

      Assertions.assertThat(orderedServiceDetailsList).isInstanceOf(DpsServices::class.java)
      Assertions.assertThat(orderedServiceDetailsList.dpsServices).isInstanceOf(List::class.java)
      Assertions.assertThat(orderedServiceDetailsList.dpsServices[0]).isInstanceOf(DpsService::class.java)
    }

    @Test
    fun `getServiceDetails extracts the correct details for the given SAR`() = runTest {
      Mockito.`when`(configOrderHelper.extractServicesConfig(any())).thenReturn(
        DpsServices(mutableListOf(DpsService(name = "test-dps-service-2", businessName = "Test DPS Service 2", orderPosition = 1, url = null)))
      )

      val orderedServiceDetails = subjectAccessRequestWorkerService.getServiceDetails(sampleSAR)
      Assertions.assertThat(orderedServiceDetails.dpsServices[0].name).isEqualTo("test-dps-service-2")
      Assertions.assertThat(orderedServiceDetails.dpsServices[0].businessName).isEqualTo("Test DPS Service 2")
      Assertions.assertThat(orderedServiceDetails.dpsServices[0].url).isEqualTo("https://test-dps-service-2.prison.service.justice.gov.uk")
      Assertions.assertThat(orderedServiceDetails.dpsServices[0].orderPosition).isEqualTo(1)
      Assertions.assertThat(orderedServiceDetails.dpsServices.size).isEqualTo(2)
    }
  }
}
