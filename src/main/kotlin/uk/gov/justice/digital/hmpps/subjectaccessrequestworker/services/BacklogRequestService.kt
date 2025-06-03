package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import java.util.UUID

@Service
class BacklogRequestService(
  private val backlogRequestRepository: BacklogRequestRepository,
  private val dynamicServicesClient: DynamicServicesClient,
) {

  fun getAllBacklogRequests(): List<BacklogRequest> = backlogRequestRepository.findAll()

  fun getById(requestId: UUID): BacklogRequest? = backlogRequestRepository.findByIdOrNull(requestId)

  fun newBacklogRequest(request: BacklogRequest): BacklogRequest = try {
    backlogRequestRepository.save(request)
  } catch (ex: DataIntegrityViolationException) {
    throw BacklogRequestException("Could not create a new BacklogRequest: unique constraint violated", ex)
  }

  fun getNextToProcess(): BacklogRequest? = backlogRequestRepository.getNextToProcess()

  @Transactional
  fun claimRequest(id: UUID): Boolean = backlogRequestRepository.claimRequest(id) == 1

  @Transactional
  fun completeRequest(id: UUID): Boolean = backlogRequestRepository.completeRequest(id) == 1

  fun getPendingServiceSummariesForId(id: UUID): List<ServiceConfiguration> = backlogRequestRepository
    .getPendingServiceSummariesForRequestId(id)

  fun getServiceSummary(request: BacklogRequest, service: ServiceConfiguration): ServiceSummary {
    return dynamicServicesClient.getServiceSummary(service, request)
      ?.let { response -> response.body?.toServiceSummary(request) }
      ?: throw BacklogRequestException(msg = "TODO", cause = null)
  }

  @Transactional
  fun addServiceSummary(request: BacklogRequest, summary: ServiceSummary) {
    backlogRequestRepository.findByIdOrNull(request.id)?.let {
      it.serviceSummary.add(summary)
      // TODO need to only add entries that don't exist
      backlogRequestRepository.save(it)
    }
  }

  fun DynamicServicesClient.SubjectDataHeldResponse.toServiceSummary(backlogRequest: BacklogRequest) = ServiceSummary(
    serviceName = this.serviceName!!,
    backlogRequest = backlogRequest,
    dataHeld = this.dataHeld,
    status = BacklogRequestStatus.COMPLETE,
  )
}