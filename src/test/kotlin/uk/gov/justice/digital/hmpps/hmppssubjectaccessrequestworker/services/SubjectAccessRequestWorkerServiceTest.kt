package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SubjectAccessRequestWorkerServiceTest {
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
    val mockClientService = Mockito.mock(WebClientService::class.java)
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

    Mockito.`when`(mockClientService.getUnclaimedSars(mockClient)).thenReturn(arrayOf(sampleSAR))

    val result = SubjectAccessRequestWorkerService(mockClientService)
      .pollForNewSubjectAccessRequests(mockClient)

    val expected: SubjectAccessRequest = sampleSAR
    Assertions.assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `startPolling calls pollForNewSubjectAccessRequests`() {
    val mockClient = Mockito.mock(WebClient::class.java)
    val webClientServiceMock = Mockito.mock(WebClientService::class.java)
    Mockito.`when`(webClientServiceMock.getClient("https://localhost:3000")).thenReturn(mockClient)
    Mockito.`when`(webClientServiceMock.getUnclaimedSars(mockClient)).thenReturn(arrayOf(sampleSAR))
    SubjectAccessRequestWorkerService(webClientServiceMock).startPolling()
    verify(webClientServiceMock, Mockito.times(1)).getUnclaimedSars(mockClient)
  }

  @Test
  fun `startPolling calls claim and complete on happy path`() {
    val mockClient = Mockito.mock(WebClient::class.java)
    val webClientServiceMock = Mockito.mock(WebClientService::class.java)
    Mockito.`when`(webClientServiceMock.getClient("https://localhost:3000")).thenReturn(mockClient)
    Mockito.`when`(webClientServiceMock.getUnclaimedSars(mockClient)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(webClientServiceMock.claim(mockClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(200))
    Mockito.`when`(webClientServiceMock.complete(mockClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(200))
    SubjectAccessRequestWorkerService(webClientServiceMock).startPolling()
    verify(webClientServiceMock, Mockito.times(1)).claim(mockClient, sampleSAR)
    verify(webClientServiceMock, Mockito.times(1)).complete(mockClient, sampleSAR)
  }

  @Test
  fun `startPolling doesn't call complete if claim patch fails`() {
    val mockClient = Mockito.mock(WebClient::class.java)
    val webClientServiceMock = Mockito.mock(WebClientService::class.java)
    Mockito.`when`(webClientServiceMock.getClient("https://localhost:3000")).thenReturn(mockClient)
    Mockito.`when`(webClientServiceMock.getUnclaimedSars(mockClient)).thenReturn(arrayOf(sampleSAR))
    Mockito.`when`(webClientServiceMock.claim(mockClient, sampleSAR)).thenReturn(HttpStatusCode.valueOf(400))
    SubjectAccessRequestWorkerService(webClientServiceMock).startPolling()
    verify(webClientServiceMock, Mockito.times(1)).claim(mockClient, sampleSAR)
    verify(webClientServiceMock, Mockito.times(0)).complete(mockClient, sampleSAR)
  }
}