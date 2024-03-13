package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Document
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.DocumentStorageGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

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

  private val mockSarGateway = Mockito.mock(SubjectAccessRequestGateway::class.java)
  private val mockGetSubjectAccessRequestDataService = Mockito.mock(GetSubjectAccessRequestDataService::class.java)
  private val mockGeneratePdfService = Mockito.mock(GeneratePdfService::class.java)
  private val mockDocument = Mockito.mock(Document::class.java)
  private val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)

  val subjectAccessRequestWorkerService = SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, "http://localhost:8080")

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

    val result = SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, "http://localhost:8080")
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
    SubjectAccessRequestWorkerService(websarGatewayMock, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, "http://localhost:8080").doPoll()
    verify(websarGatewayMock, Mockito.times(1)).getUnclaimed(mockClient)
  }

  @Test
  fun `startPolling calls claim and complete on happy path`() = runTest {
    val mockClient = Mockito.mock(WebClient::class.java)
    val websarGatewayMock = Mockito.mock(SubjectAccessRequestGateway::class.java)
    val pdfStreamMock = Mockito.mock(ByteArrayOutputStream::class.java)
    Mockito.`when`(websarGatewayMock.getClient("http://localhost:8080")).thenReturn(mockClient)
    Mockito.`when`(websarGatewayMock.getUnclaimed(mockClient)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(websarGatewayMock.claim(mockClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(200))
    Mockito.`when`(websarGatewayMock.complete(mockClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(200))
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(services = mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk", "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"), null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGeneratePdfService.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
      .thenReturn(mockStream)
    Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), pdfStreamMock))
      .thenReturn("")
    SubjectAccessRequestWorkerService(websarGatewayMock, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, "http://localhost:8080").doPoll()
    verify(websarGatewayMock, Mockito.times(1)).claim(mockClient, sampleSAR)
    verify(websarGatewayMock, Mockito.times(1)).complete(mockClient, sampleSAR)
  }

  @Test
  fun `startPolling doesn't call complete if claim patch fails`() = runTest {
    val mockClient = Mockito.mock(WebClient::class.java)
    val websarGatewayMock = Mockito.mock(SubjectAccessRequestGateway::class.java)
    Mockito.`when`(websarGatewayMock.getClient("http://localhost:8080")).thenReturn(mockClient)
    Mockito.`when`(websarGatewayMock.getUnclaimed(mockClient)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(websarGatewayMock.claim(mockClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(400))
    SubjectAccessRequestWorkerService(websarGatewayMock, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, "http://localhost:8080").doPoll()
    verify(websarGatewayMock, Mockito.times(1)).claim(mockClient, sampleSAR)
    verify(websarGatewayMock, Mockito.times(0)).complete(mockClient, sampleSAR)
  }

  @Test
  fun `doReport calls getSubjectAccessRequestDataService with chosenSar details`() {
    val pdfStreamMock = Mockito.mock(ByteArrayOutputStream::class.java)
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(services = mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk", "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"), null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(services = mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk", "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"), null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGeneratePdfService.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
      .thenReturn(mockStream)
    Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), pdfStreamMock))
      .thenReturn("")
    subjectAccessRequestWorkerService.doReport(sampleSAR)

    verify(mockGetSubjectAccessRequestDataService, Mockito.times(1)).execute(services = mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk", "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"), null, "1", dateFromFormatted, dateToFormatted)
  }

  @Test
  fun `doReport throws exception if an error occurs during attempt to retrieve upstream API info`() {
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(services = mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk", "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"), null, "1", dateFromFormatted, dateToFormatted))
      .thenThrow(RuntimeException())
    val exception = shouldThrow<RuntimeException> {
      subjectAccessRequestWorkerService.doReport(sampleSAR)
    }
    exception.message.shouldBe(null)
  }

  @Test
  fun `doReport calls generatePdfService`() {
    val pdfStreamMock = Mockito.mock(ByteArrayOutputStream::class.java)
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk"), null, "1", dateFromFormatted, dateToFormatted))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGeneratePdfService.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
      .thenReturn(mockStream)
    Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), pdfStreamMock))
      .thenReturn("")
    subjectAccessRequestWorkerService.doReport(sampleSAR)
    verify(mockGeneratePdfService, Mockito.times(1)).execute(any(), any(), any(), any(), any(), any(), any(), any(), any())
  }

  @Test
  fun `doReport calls storeSubjectAccessRequestDocument`() = runTest {
    Mockito.`when`(mockGetSubjectAccessRequestDataService.execute(services = mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk", "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"), sampleSAR.nomisId, sampleSAR.ndeliusCaseReferenceId, sampleSAR.dateFrom, sampleSAR.dateTo))
      .thenReturn(mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
    Mockito.`when`(mockGeneratePdfService.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
      .thenReturn(mockStream)
    Mockito.`when`(documentGateway.storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream)).thenReturn("Random string")
    SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, "http://localhost:8080").doReport(sampleSAR)
    verify(documentGateway, Mockito.times(1)).storeDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), mockStream)
  }
}
