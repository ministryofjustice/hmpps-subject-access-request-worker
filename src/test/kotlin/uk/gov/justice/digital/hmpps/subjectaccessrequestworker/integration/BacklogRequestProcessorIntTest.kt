package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.PENDING
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

const val TIMEOUT_SEC = 8L

class BacklogRequestProcessorIntTest : BaseBacklogRequestIntTest() {

  override fun customSetup() {
    // no custom set up required
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "true   | 'Completed backlog request should show data held TRUE when services holds data on subject'",
      "false  | 'Completed backlog request should show data held FALSE when services does not hold data on subject'",
    ],
    delimiter = '|',
  )
  fun `backlog request is successfully processed when subject data is held`(
    dataIsHeld: Boolean,
    description: String,
  ) {
    val start = LocalDateTime.now()
    val backlogRequest = createBacklogRequest()
    assertThat(backlogRequest).isNotNull

    hmppsAuth.stubGrantToken()
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-1"), dataIsHeld)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-2"), dataIsHeld)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-3"), dataIsHeld)

    await()
      .atMost(TIMEOUT_SEC, TimeUnit.SECONDS)
      .until { requestIsComplete(backlogRequest!!.id) }

    val result = assertBacklogRequestEqualsExpected(
      backlogRequestId = backlogRequest!!.id,
      createdAfter = start,
      expectedDataHeld = dataIsHeld,
      expectedStatus = COMPLETE,
      expectedClaimDateTimeAfter = start,
    )

    assertThat(result.serviceSummary).hasSize(serviceConfigurations.size)
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-1",
      expectedOrder = 1,
      expectedDataHeld = dataIsHeld,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-2",
      expectedOrder = 2,
      expectedDataHeld = dataIsHeld,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-3",
      expectedOrder = 3,
      expectedDataHeld = dataIsHeld,
      expectedStatus = COMPLETE,
    )

    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-1"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-2"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-3"))
  }

  @Test
  fun `backlog request is successful with data held TRUE when some services do not hold subject data`() {
    val start = LocalDateTime.now()
    val backlogRequest = createBacklogRequest()
    assertThat(backlogRequest).isNotNull

    hmppsAuth.stubGrantToken()
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-1"), false)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-2"), false)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-3"), true)

    await()
      .atMost(TIMEOUT_SEC, TimeUnit.SECONDS)
      .until { requestIsComplete(backlogRequest!!.id) }

    val result = assertBacklogRequestEqualsExpected(
      backlogRequestId = backlogRequest!!.id,
      createdAfter = start,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
      expectedClaimDateTimeAfter = start,
    )

    val serviceConfigurations = serviceConfigurationService.getAllServiceConfigurations()
    assertThat(result.serviceSummary).hasSize(serviceConfigurations.size)

    assertThat(result.serviceSummary).hasSize(serviceConfigurations.size)
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-1",
      expectedOrder = 1,
      expectedDataHeld = false,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-2",
      expectedOrder = 2,
      expectedDataHeld = false,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-3",
      expectedOrder = 3,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
    )

    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-1"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-2"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-3"))
  }

  @Test
  fun `processor will process successful requests even if 1 or more requests fails with error`() = runTest {
    val start = LocalDateTime.now()
    val backlogRequest = createBacklogRequest()
    assertThat(backlogRequest).isNotNull

    hmppsAuth.stubGrantToken()

    // Request 1 & 2 are successful, request 3 fails with status 500
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-1"), false)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-2"), false)
    stubRendererSubjectDataHeldResponseError(createSubjectDataHeldRequest("service-3"), 500)

    await()
      .atMost(TIMEOUT_SEC, TimeUnit.SECONDS)
      .until { renderMockIsCalledNTimes(n = 3) && requestHasAtLeastNCompleteSummaries(id = backlogRequest!!.id, n = 2) }

    val result = assertBacklogRequestIsNotComplete(
      backlogRequestId = backlogRequest!!.id,
      expectedClaimDateTimeAfter = start,
    )

    assertThat(result.serviceSummary).hasSize(2)

    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-1",
      expectedOrder = 1,
      expectedDataHeld = false,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-2",
      expectedOrder = 2,
      expectedDataHeld = false,
      expectedStatus = COMPLETE,
    )

    assertServiceSummaryDoesNotExist(
      backlogRequestId = result.id,
      serviceName = "service-3",
      status = COMPLETE,
    )

    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-1"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-2"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-3"))
  }

  @Test
  fun `backlog request only queries enabled services`() {
    val start = LocalDateTime.now()
    val backlogRequest = createBacklogRequest()
    assertThat(backlogRequest).isNotNull

    serviceConfigurationService.disableService(serviceConfigurations[0].id)

    hmppsAuth.stubGrantToken()
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-2"), true)
    stubRendererSubjectDataHeldResponse(createSubjectDataHeldRequest("service-3"), false)

    await()
      .atMost(TIMEOUT_SEC, TimeUnit.SECONDS)
      .until { requestIsComplete(backlogRequest!!.id) }

    val result = assertBacklogRequestEqualsExpected(
      backlogRequestId = backlogRequest!!.id,
      createdAfter = start,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
      expectedClaimDateTimeAfter = start,
    )

    assertThat(result.serviceSummary).hasSize(2)
    assertServiceSummaryDoesNotExist(
      backlogRequestId = result.id,
      serviceName = "service-1",
      status = COMPLETE,
    )
    assertServiceSummaryDoesNotExist(
      backlogRequestId = result.id,
      serviceName = "service-1",
      status = PENDING,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-2",
      expectedOrder = 2,
      expectedDataHeld = true,
      expectedStatus = COMPLETE,
    )
    assertServiceSummaryExistsWithExpectedValues(
      backlogRequestId = result.id,
      serviceName = "service-3",
      expectedOrder = 3,
      expectedDataHeld = false,
      expectedStatus = COMPLETE,
    )

    htmlRendererApi.verifySubjectDataHeldSummaryCalled(0, createSubjectDataHeldRequest("service-1"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-2"))
    htmlRendererApi.verifySubjectDataHeldSummaryCalled(1, createSubjectDataHeldRequest("service-3"))
  }

  private fun assertBacklogRequestEqualsExpected(
    backlogRequestId: UUID,
    createdAfter: LocalDateTime,
    expectedDataHeld: Boolean,
    expectedStatus: BacklogRequestStatus,
    expectedClaimDateTimeAfter: LocalDateTime?,
  ): BacklogRequest {
    val backlogRequest = backlogRequestRepository.findByIdOrNull(backlogRequestId)
    assertThat(backlogRequest).isNotNull
    assertThat(backlogRequest!!.status).isEqualTo(expectedStatus)
    assertThat(backlogRequest.completedAt).isNotNull()
    assertThat(backlogRequest.completedAt).isBetween(createdAfter, LocalDateTime.now())
    assertThat(backlogRequest.dataHeld).isEqualTo(expectedDataHeld)
    expectedClaimDateTimeAfter?.let {
      assertThat(backlogRequest.claimDateTime).isBetween(it, LocalDateTime.now())
    }
    return backlogRequest
  }

  private fun assertBacklogRequestIsNotComplete(
    backlogRequestId: UUID,
    expectedClaimDateTimeAfter: LocalDateTime?,
  ): BacklogRequest {
    val backlogRequest = backlogRequestRepository.findByIdOrNull(backlogRequestId)
    assertThat(backlogRequest).isNotNull
    assertThat(backlogRequest!!.status).isEqualTo(PENDING)
    assertThat(backlogRequest.completedAt).isNull()
    assertThat(backlogRequest.dataHeld).isNull()
    expectedClaimDateTimeAfter?.let {
      assertThat(backlogRequest.claimDateTime).isBetween(it, LocalDateTime.now())
    }
    return backlogRequest
  }

  private fun assertServiceSummaryDoesNotExist(
    backlogRequestId: UUID,
    serviceName: String,
    status: BacklogRequestStatus,
  ) {
    val serviceSummary = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceConfigurationServiceNameAndStatus(
      backlogRequestId = backlogRequestId,
      serviceName = serviceName,
      status = status,
    )
    assertThat(serviceSummary).isNull()
  }

  private fun assertServiceSummaryExistsWithExpectedValues(
    backlogRequestId: UUID,
    serviceName: String,
    expectedOrder: Int,
    expectedDataHeld: Boolean,
    expectedStatus: BacklogRequestStatus,
  ) {
    val serviceSummary = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceConfigurationServiceNameAndStatus(
      backlogRequestId = backlogRequestId,
      serviceName = serviceName,
      status = COMPLETE,
    )
    assertThat(serviceSummary).isNotNull

    assertThat(serviceSummary!!.serviceConfiguration?.id).isNotNull()
    assertThat(serviceSummary.backlogRequest).isNotNull
    assertThat(serviceSummary.backlogRequest!!.id).isEqualTo(backlogRequestId)
    assertThat(serviceSummary.dataHeld).isEqualTo(expectedDataHeld)
    assertThat(serviceSummary.status).isEqualTo(expectedStatus)
  }
}
