package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.controller

import io.sentry.Sentry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services.SubjectAccessRequestWorkerService

class SubjectAccessRequestControllerTest: IntegrationTestBase() {

  @Test
  suspend fun `startPolling exceptions are captured by sentry`() {
    val mockSentry = Mockito.mock(Sentry::class.java)
    val mockSubjectAccessRequestWorkerService = Mockito.mock(SubjectAccessRequestWorkerService::class.java)

    Mockito.`when`(mockSubjectAccessRequestWorkerService.startPolling())
      .thenThrow(RuntimeException())

    mockSubjectAccessRequestWorkerService.startPolling()

    verify(mockSentry, Mockito.times(1)).captureException(Any())
  }
}