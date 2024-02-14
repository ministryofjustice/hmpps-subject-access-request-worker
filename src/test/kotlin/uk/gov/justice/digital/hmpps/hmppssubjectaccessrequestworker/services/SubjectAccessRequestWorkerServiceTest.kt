package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
class SubjectAccessRequestWorkerServiceTest : IntegrationTestBase() {
  private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private val dateFrom = "02/01/2023"
  private val dateFromFormatted = LocalDate.parse(dateFrom, formatter)
  private val dateTo = "02/01/2024"
  private val dateToFormatted = LocalDate.parse(dateTo, formatter)
  private val requestTime = LocalDateTime.now()
  private val sampleSAR = SubjectAccessRequest(
    id = null,
    status = Status.Pending,
    dateFrom = dateFromFormatted,
    dateTo = dateToFormatted,
    sarCaseReferenceNumber = "1234abc",
    services = "{1,2,4}",
    nomisId = "",
    ndeliusCaseReferenceId = "1",
    requestedBy = "aName",
    requestDateTime = requestTime,
    claimAttempts = 0,
  )

  @Test
  fun `pollForNewSubjectAccessRequests returns single SubjectAccessRequest`() {
    val mocksarGateway = Mockito.mock(SubjectAccessRequestGateway::class.java)
    val mockClient = Mockito.mock(WebClient::class.java)
    val mockToken = "testtoken"
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

    Mockito.`when`(mocksarGateway.getUnclaimed(mockClient, mockToken)).thenReturn(arrayOf(sampleSAR))

    val result = SubjectAccessRequestWorkerService(mocksarGateway, "true", "http://localhost:8080")
      .pollForNewSubjectAccessRequests(mockClient, mockToken)

    val expected: SubjectAccessRequest = sampleSAR
    Assertions.assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `startPolling calls pollForNewSubjectAccessRequests`() {
    val mockClient = Mockito.mock(WebClient::class.java)
    val mockToken = "testtoken"
    val websarGatewayMock = Mockito.mock(SubjectAccessRequestGateway::class.java)
    Mockito.`when`(websarGatewayMock.getClient("http://localhost:8080")).thenReturn(mockClient)
    Mockito.`when`(websarGatewayMock.getClientTokenFromHmppsAuth()).thenReturn(mockToken)
    Mockito.`when`(websarGatewayMock.getUnclaimed(mockClient, mockToken)).thenReturn(arrayOf(sampleSAR))
    SubjectAccessRequestWorkerService(websarGatewayMock, "true", "http://localhost:8080").startPolling()
    verify(websarGatewayMock, Mockito.times(1)).getUnclaimed(mockClient, mockToken)
  }

  @Test
  fun `startPolling calls claim and complete on happy path`() {
    val mockClient = Mockito.mock(WebClient::class.java)
    val mockToken = "testtoken"
    val websarGatewayMock = Mockito.mock(SubjectAccessRequestGateway::class.java)
    Mockito.`when`(websarGatewayMock.getClient("http://localhost:8080")).thenReturn(mockClient)
    Mockito.`when`(websarGatewayMock.getClientTokenFromHmppsAuth()).thenReturn(mockToken)
    Mockito.`when`(websarGatewayMock.getUnclaimed(mockClient, mockToken)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(websarGatewayMock.claim(mockClient, sampleSAR, mockToken)).thenReturn(HttpStatusCode.valueOf(200))
    Mockito.`when`(websarGatewayMock.complete(mockClient, sampleSAR, mockToken)).thenReturn(HttpStatusCode.valueOf(200))
    SubjectAccessRequestWorkerService(websarGatewayMock, "true", "http://localhost:8080").startPolling()
    verify(websarGatewayMock, Mockito.times(1)).claim(mockClient, sampleSAR, mockToken)
    verify(websarGatewayMock, Mockito.times(1)).complete(mockClient, sampleSAR, mockToken)
  }

  @Test
  fun `startPolling doesn't call complete if claim patch fails`() {
    val mockClient = Mockito.mock(WebClient::class.java)
    val mockToken = "testtoken"
    val websarGatewayMock = Mockito.mock(SubjectAccessRequestGateway::class.java)
    Mockito.`when`(websarGatewayMock.getClient("http://localhost:8080")).thenReturn(mockClient)
    Mockito.`when`(websarGatewayMock.getClientTokenFromHmppsAuth()).thenReturn(mockToken)
    Mockito.`when`(websarGatewayMock.getUnclaimed(mockClient, mockToken)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(websarGatewayMock.claim(mockClient, sampleSAR, mockToken)).thenReturn(HttpStatusCode.valueOf(400))
    SubjectAccessRequestWorkerService(websarGatewayMock, "true", "http://localhost:8080").startPolling()
    verify(websarGatewayMock, Mockito.times(1)).claim(mockClient, sampleSAR, mockToken)
    verify(websarGatewayMock, Mockito.times(0)).complete(mockClient, sampleSAR, mockToken)
  }
}
