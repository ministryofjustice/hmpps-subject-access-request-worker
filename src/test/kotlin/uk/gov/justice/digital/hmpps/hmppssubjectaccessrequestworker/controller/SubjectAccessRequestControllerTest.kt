package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.controller

import io.mockk.mockkStatic
import io.mockk.verify
import io.sentry.Sentry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.controllers.SubjectAccessRequestWorkerController
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services.SubjectAccessRequestWorkerService

class SubjectAccessRequestControllerTest : IntegrationTestBase() {

  @Test
  fun `startPolling exceptions are captured by sentry`() = runTest {
    // val mockSentry = Mockito.mock(Sentry::class.java)
    mockkStatic(Sentry::class)
    val mockSubjectAccessRequestWorkerService = Mockito.mock(SubjectAccessRequestWorkerService::class.java)
    Mockito.`when`(mockSubjectAccessRequestWorkerService.startPolling())
      .thenThrow(RuntimeException())
    SubjectAccessRequestWorkerController(mockSubjectAccessRequestWorkerService).startPolling()
    // verify(mockSentry, Mockito.times(1)).captureException(Any())
    verify(exactly = 1) {
      Sentry.captureException(
        any(),
      )
    }
  }
}
