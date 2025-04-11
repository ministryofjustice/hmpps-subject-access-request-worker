package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting.AlertsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.ReportService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.SubjectAccessRequestService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SubjectAccessRequestProcessorTest {

  @Captor
  private lateinit var eventNameCaptor: ArgumentCaptor<String>

  @Captor
  private lateinit var eventPropertiesCaptor: ArgumentCaptor<Map<String, String>>

  @Captor
  private lateinit var alertExceptionCaptor: ArgumentCaptor<SubjectAccessRequestException>

  private val reportService: ReportService = mock()
  private val subjectAccessRequestService: SubjectAccessRequestService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val alertsService: AlertsService = mock()

  private val dateFromFormatted = LocalDate.parse(
    "02/01/2023",
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
  )

  private val dateToFormatted = LocalDate.parse(
    "02/01/2024",
    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
  )

  private val sampleSAR = SubjectAccessRequest(
    id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
    status = Status.Pending,
    dateFrom = dateFromFormatted,
    dateTo = dateToFormatted,
    sarCaseReferenceNumber = "1234abc",
    services = "service-a,service-b",
    nomisId = null,
    ndeliusCaseReferenceId = "1",
    requestedBy = "aName",
    requestDateTime = LocalDateTime.now(),
    claimAttempts = 0,
    contextId = UUID.randomUUID(),
  )

  private val subjectAccessRequestProcessor = SubjectAccessRequestProcessor(
    reportService = reportService,
    subjectAccessRequestService = subjectAccessRequestService,
    alertsService = alertsService,
    telemetryClient = telemetryClient,
  )

  @Nested
  inner class SuccessCases {

    @Test
    fun `should generate report for available unclaimed request`() = runTest {
      whenever(subjectAccessRequestService.findUnclaimed()).thenReturn(listOf(sampleSAR))

      subjectAccessRequestProcessor.execute()

      verify(subjectAccessRequestService, times(1)).findUnclaimed()
      verify(subjectAccessRequestService, times(1)).updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(sampleSAR.id)
      verify(reportService, times(1)).generateReport(sampleSAR)
      verify(subjectAccessRequestService, times(1)).updateStatus(sampleSAR.id, Status.Completed)
      verifyNoInteractions(alertsService)
      verifyTelemetryEvents(sampleSAR, "NewReportClaimStarted", "NewReportClaimComplete")
    }

    @Test
    fun `should do nothing when findUnclaimed returns empty list`() = runTest {
      whenever(subjectAccessRequestService.findUnclaimed()).thenReturn(emptyList())

      subjectAccessRequestProcessor.execute()

      verify(subjectAccessRequestService, times(1)).findUnclaimed()
      verifyNoMoreInteractions(subjectAccessRequestService)
      verifyNoInteractions(reportService, telemetryClient)
    }

    @Test
    fun `should do nothing when findUnclaimed returns list with null value at index 0`() = runTest {
      whenever(subjectAccessRequestService.findUnclaimed()).thenReturn(listOf(null))

      subjectAccessRequestProcessor.execute()

      verify(subjectAccessRequestService, times(1)).findUnclaimed()
      verifyNoMoreInteractions(subjectAccessRequestService)
      verifyNoInteractions(reportService, telemetryClient)
    }
  }

  @Nested
  inner class FailureCases {

    @Test
    fun `should raise expected alert when findUnclaimed throws an exception`() = runTest {
      val rootCause = RuntimeException("findUnclaimed error")
      whenever(subjectAccessRequestService.findUnclaimed()).thenThrow(rootCause)

      subjectAccessRequestProcessor.execute()

      verify(subjectAccessRequestService, times(1)).findUnclaimed()
      verifyNoMoreInteractions(subjectAccessRequestService)
      verifyTelemetryEvents(null, "ReportFailedWithError")
      verifyNoInteractions(reportService)
      verifySubjectAccessRequestAlert(
        expectedCause = rootCause,
        subjectAccessRequest = null,
      )
    }

    @Test
    fun `should raise expected alert when reportService throws an exception`() = runTest {
      val rootCause = RuntimeException("findUnclaimed error")
      whenever(subjectAccessRequestService.findUnclaimed()).thenReturn(listOf(sampleSAR))
      whenever(reportService.generateReport(sampleSAR)).thenThrow(rootCause)

      subjectAccessRequestProcessor.execute()

      verify(subjectAccessRequestService, times(1)).findUnclaimed()
      verify(reportService, times(1)).generateReport(sampleSAR)
      verify(subjectAccessRequestService, times(1)).updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(sampleSAR.id)
      verifyNoMoreInteractions(subjectAccessRequestService, reportService)
      verifyTelemetryEvents(sampleSAR, "NewReportClaimStarted", "ReportFailedWithError")

      verifySubjectAccessRequestAlert(
        expectedCause = rootCause,
        subjectAccessRequest = sampleSAR,
      )
    }

    @Test
    fun `should raise expected alert when updateStatus throws an exception`() = runTest {
      val rootCause = RuntimeException("findUnclaimed error")
      whenever(subjectAccessRequestService.findUnclaimed()).thenReturn(listOf(sampleSAR))
      whenever(subjectAccessRequestService.updateStatus(sampleSAR.id, Status.Completed)).thenThrow(rootCause)

      subjectAccessRequestProcessor.execute()

      verify(subjectAccessRequestService, times(1)).findUnclaimed()
      verify(reportService, times(1)).generateReport(sampleSAR)
      verify(subjectAccessRequestService, times(1)).updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(sampleSAR.id)
      verify(subjectAccessRequestService, times(1)).updateStatus(sampleSAR.id, Status.Completed)
      verifyNoMoreInteractions(subjectAccessRequestService, reportService)
      verifyTelemetryEvents(sampleSAR, "NewReportClaimStarted", "ReportFailedWithError")

      verifySubjectAccessRequestAlert(
        expectedCause = rootCause,
        subjectAccessRequest = sampleSAR,
      )
    }
  }

  private fun verifyTelemetryEvents(subjectAccessRequest: SubjectAccessRequest?, vararg eventNames: String) {
    verify(telemetryClient, times(eventNames.size)).trackEvent(
      capture(eventNameCaptor),
      capture(eventPropertiesCaptor),
      eq(null),
    )

    eventNames.forEachIndexed { i, eventName ->
      assertThat(eventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
      assertThat(eventNameCaptor.allValues[i]).isEqualTo(eventName)

      assertThat(eventPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
      assertThat(eventPropertiesCaptor.allValues[i]).containsEntry(
        "sarId",
        subjectAccessRequest?.sarCaseReferenceNumber ?: "unknown",
      )
      assertThat(eventPropertiesCaptor.allValues[i]).containsEntry("UUID", subjectAccessRequest?.id.toString())
      assertThat(eventPropertiesCaptor.allValues[i]).containsEntry(
        "contextId",
        subjectAccessRequest?.contextId.toString(),
      )
    }
  }

  private fun verifySubjectAccessRequestAlert(expectedCause: Exception?, subjectAccessRequest: SubjectAccessRequest?) {
    verify(alertsService, times(1)).raiseReportErrorAlert(capture(alertExceptionCaptor))
    assertThat(alertExceptionCaptor.allValues).hasSize(1)

    val actual = alertExceptionCaptor.allValues[0]
    assertThat(actual).isInstanceOf(SubjectAccessRequestException::class.java)
    assertThat(actual.message).startsWith("subject access request threw unexpected error")
    assertThat(actual.cause).isEqualTo(expectedCause)
    assertThat(actual.event).isEqualTo(null)
    assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)
    assertThat(actual.params).containsEntry("sarCaseReferenceNumber", subjectAccessRequest?.sarCaseReferenceNumber)
  }
}
