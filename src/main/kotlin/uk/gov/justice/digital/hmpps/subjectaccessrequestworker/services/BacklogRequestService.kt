package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient.SubjectDataHeldRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.PENDING
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceSummaryRepository
import java.time.LocalDateTime
import java.util.UUID

@Service
class BacklogRequestService(
  private val backlogRequestRepository: BacklogRequestRepository,
  private val serviceSummaryRepository: ServiceSummaryRepository,
  private val serviceConfigurationService: ServiceConfigurationService,
  private val dynamicServicesClient: DynamicServicesClient,
) {

  private companion object {
    private val LOG = LoggerFactory.getLogger(BacklogRequestService::class.java)
  }

  fun newBacklogRequest(request: BacklogRequest): BacklogRequest = try {
    backlogRequestRepository.saveAndFlush(request)
  } catch (ex: DataIntegrityViolationException) {
    throw BacklogRequestException(request.id, "Could not create a new BacklogRequest: unique constraint violated", ex)
  }

  fun getRequestsForVersion(
    version: String,
  ): List<BacklogRequest> = backlogRequestRepository.findByVersionOrderByCreatedAt(version)

  fun getByIdOrNull(id: UUID): BacklogRequest? = backlogRequestRepository.findByIdOrNull(id)

  fun getVersions(): Set<String> = backlogRequestRepository.findDistinctVersions()

  fun deleteAll(): Unit = backlogRequestRepository.deleteAll()

  fun getStatusByVersion(version: String): BacklogStatus? = backlogRequestRepository
    .countByVersion(version)
    .takeIf { it > 0 }
    ?.let { total ->
      val completedRequests = backlogRequestRepository.countByVersionAndStatus(version, COMPLETE)
      val status = if (completedRequests == total) BacklogVersionStatus.COMPLETE else BacklogVersionStatus.IN_PROGRESS

      BacklogStatus(
        totalRequests = total,
        pendingRequests = backlogRequestRepository.countByVersionAndStatus(version, PENDING),
        completedRequests = completedRequests,
        completeRequestsWithDataHeld = backlogRequestRepository.countByVersionAndStatusAndDataHeld(
          version = version,
          status = COMPLETE,
          dataHeld = true,
        ),
        status = status,
      )
    }

  @Transactional
  fun deleteById(id: UUID): Unit = backlogRequestRepository.deleteById(id)

  @Transactional
  fun deleteByVersion(version: String): Int = backlogRequestRepository.deleteBacklogRequestByVersion(version)

  @Transactional
  fun getNextToProcess(): BacklogRequest? = backlogRequestRepository.getNextToProcess()

  @Transactional
  fun claimRequest(id: UUID): Boolean = (1 == backlogRequestRepository.updateClaimDateTime(id))

  @Transactional
  fun attemptCompleteRequest(id: UUID): BacklogRequest? {
    LOG.info("attempting to update backlog request: {} status to {}", id, COMPLETE)

    return backlogRequestRepository.findCompleteRequestOrNull(id)?.let {
      it.status = COMPLETE
      it.completedAt = LocalDateTime.now()
      it.dataHeld = backlogRequestRepository.findDataHeldByIdOrNull(it.id)?.let { true } ?: false

      backlogRequestRepository.saveAndFlush(it).also { savedRequest ->
        LOG.info(
          "backlog request: {} status successfully updated to {} dataHeld?: {}",
          id,
          COMPLETE,
          savedRequest.dataHeld,
        )
      }
    } ?: run {
      LOG.info("backlog request: {} status not updated - request did not meet criteria for status {}", id, COMPLETE)
      null
    }
  }

  @Transactional
  fun getPendingServiceSummariesForId(
    id: UUID,
  ): List<ServiceConfiguration> = serviceSummaryRepository.getPendingServiceSummariesForRequestId(id)

  fun getSubjectDataHeldSummary(
    backlogRequest: BacklogRequest,
    serviceConfig: ServiceConfiguration,
  ): ServiceSummary = dynamicServicesClient.getServiceSummary(
    subjectDataHeldRequest = SubjectDataHeldRequest(
      nomisId = backlogRequest.nomisId,
      ndeliusId = backlogRequest.ndeliusCaseReferenceId,
      dateFrom = backlogRequest.dateFrom,
      dateTo = backlogRequest.dateTo,
      serviceName = serviceConfig.serviceName,
      serviceUrl = serviceConfigurationService.resolveUrlPlaceHolder(serviceConfig),
    ),
  )?.let { response ->
    response.body?.let {
      ServiceSummary(
        serviceName = serviceConfig.serviceName,
        serviceOrder = serviceConfig.order,
        backlogRequest = backlogRequest,
        dataHeld = it.dataHeld,
        status = COMPLETE,
      )
    } ?: throw BacklogRequestException(
      backlogRequestId = backlogRequest.id,
      msg = "subject data held response body was null",
    )
  } ?: throw BacklogRequestException(
    backlogRequestId = backlogRequest.id,
    msg = "error getting service summary for backlog request ${backlogRequest.id}",
  )

  @Transactional
  fun addServiceSummary(request: BacklogRequest, summary: ServiceSummary) {
    if (summary.serviceName.isEmpty()) {
      throw BacklogRequestException(request.id, "Service name cannot be empty")
    }

    if (serviceConfigurationService.findByServiceName(summary.serviceName) == null) {
      throw BacklogRequestException(request.id, "Service Configuration does not exist for serviceName")
    }

    serviceSummaryRepository.findOneByBacklogRequestIdAndServiceName(request.id, summary.serviceName)
      ?.let { existingSummary -> updateExistingServiceSummary(request, existingSummary, summary) }
      ?: addNewServiceSummary(request, summary)
  }

  private fun updateExistingServiceSummary(
    backlogRequest: BacklogRequest,
    saved: ServiceSummary,
    latest: ServiceSummary,
  ) {
    LOG.info(
      "updating existing service summary for backlogRequest={}, serviceName={}, dataHeld={}",
      backlogRequest.id,
      saved.serviceName,
      saved.dataHeld,
    )

    saved.serviceOrder = latest.serviceOrder
    saved.status = latest.status
    saved.dataHeld = latest.dataHeld
    serviceSummaryRepository.saveAndFlush(saved)
  }

  private fun addNewServiceSummary(backlogRequest: BacklogRequest, summary: ServiceSummary) {
    LOG.info(
      "adding new service summary for backlogRequest={}, serviceName={}, dataHeld={}",
      backlogRequest.id,
      summary.serviceName,
      summary.dataHeld,
    )

    summary.backlogRequest = backlogRequest
    serviceSummaryRepository.saveAndFlush(summary)
  }

  data class BacklogStatus(
    val totalRequests: Long,
    val pendingRequests: Long,
    val completedRequests: Long,
    val completeRequestsWithDataHeld: Long,
    val status: BacklogVersionStatus,
  )

  enum class BacklogVersionStatus {
    COMPLETE,
    IN_PROGRESS,
  }
}
