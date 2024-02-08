package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.controllers

import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestapi.models.Status
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestapi.models.SubjectAccessRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SubjectAccessRequestWorkerControllerTest {
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

//  @Test
//  fun `pollForNewSubjectAccessRequests returns single SubjectAccessRequests`() {
//    val mockClient = Mockito.mock(WebClient::class.java)
//    Mockito.`when`(
//      mockClient.get().uri("/api/subjectAccessRequests?unclaimed=true").retrieve()
//        .bodyToMono(Array<SubjectAccessRequest>::class.java).block(),
//    ).thenReturn(arrayOf(sampleSAR))
//    val result = SubjectAccessRequestWorkerController()
//      .pollForNewSubjectAccessRequests(mockClient)
//
//    val expected: SubjectAccessRequest = sampleSAR
//    // verify(sarService, times(1)).createSubjectAccessRequest(ndeliusRequest, authentication, requestTime)
//    Assertions.assertThat(result).isEqualTo(expected)
//  }
}
