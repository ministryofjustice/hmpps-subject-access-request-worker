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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceSummaryRepository
import java.util.UUID

@Service
class BacklogRequestService(
  private val backlogRequestRepository: BacklogRequestRepository,
  private val serviceSummaryRepository: ServiceSummaryRepository,
  private val serviceConfigurationRepository: ServiceConfigurationRepository,
  private val dynamicServicesClient: DynamicServicesClient,
) {

  private companion object {
    private val LOG = LoggerFactory.getLogger(BacklogRequestService::class.java)
  }

  fun save(backlogRequest: BacklogRequest): BacklogRequest = backlogRequestRepository.saveAndFlush(backlogRequest)

  fun getAllBacklogRequests(): List<BacklogRequest> = backlogRequestRepository.findAll()

  fun getByIdOrNull(id: UUID): BacklogRequest? = backlogRequestRepository.findByIdOrNull(id)

  fun newBacklogRequest(request: BacklogRequest): BacklogRequest = try {
    backlogRequestRepository.save(request)
  } catch (ex: DataIntegrityViolationException) {
    throw BacklogRequestException(request.id, "Could not create a new BacklogRequest: unique constraint violated", ex)
  }

  @Transactional
  fun getNextToProcess(): BacklogRequest? = backlogRequestRepository.getNextToProcess()

  @Transactional
  fun claimRequest(id: UUID): Boolean = (1 == backlogRequestRepository.updateClaimDateTime(id))

  @Transactional
  fun completeRequest(id: UUID): Boolean = 1 == backlogRequestRepository.updateStatusToComplete(id)

  @Transactional
  fun getPendingServiceSummariesForId(
    id: UUID,
  ): List<ServiceConfiguration> = serviceSummaryRepository.getPendingServiceSummariesForRequestId(id)

  fun getServiceSummary(
    backlogRequest: BacklogRequest,
    serviceConfig: ServiceConfiguration,
  ): ServiceSummary = dynamicServicesClient.getServiceSummary(
    subjectDataHeldRequest = SubjectDataHeldRequest(
      nomisId = backlogRequest.nomisId,
      ndeliusId = backlogRequest.ndeliusCaseReferenceId,
      dateFrom = backlogRequest.dateFrom,
      dateTo = backlogRequest.dateTo,
      serviceName = serviceConfig.serviceName,
      serviceUrl = serviceConfig.url,
    ),
  )?.let { response ->
    response.body?.let {
      ServiceSummary(
        serviceName = serviceConfig.serviceName,
        serviceOrder = serviceConfig.order,
        backlogRequest = backlogRequest,
        dataHeld = it.dataHeld,
        status = BacklogRequestStatus.COMPLETE,
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

    if (serviceConfigurationRepository.findByServiceName(summary.serviceName) == null) {
      throw BacklogRequestException(request.id, "Service Configuration does not exist for serviceName")
    }

    serviceSummaryRepository.findOneByBacklogRequestIdAndServiceName(request.id, summary.serviceName)?.let {
      LOG.info("updating existing service summary for backlogRequestId=${request.id}, serviceName=${it.serviceName}")
      it.serviceOrder = summary.serviceOrder
      it.status = summary.status
      it.dataHeld = summary.dataHeld
      serviceSummaryRepository.save(summary)
    } ?: run {
      LOG.info("adding service summary to backlogRequestId=${request.id}, serviceName=${summary.serviceName}")
      backlogRequestRepository.findByIdOrNull(request.id)?.let {
        it.addServiceSummaries(summary)
        backlogRequestRepository.saveAndFlush(it)
      }
    }
  }
}
