package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.GENERATE_REPORT_RENDER_ALL_COMPLETED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.UPDATE_SAR_SERVICE_RENDER_STATUS
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.SERVICES_NOT_RENDERED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.SubjectAccessRequestRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.TemplateVersionRepository
import java.time.LocalDateTime
import java.util.UUID

@Service
class SubjectAccessRequestService(
  private val subjectAccessRequestRepository: SubjectAccessRequestRepository,
  private val templateVersionRepository: TemplateVersionRepository,
  @param:Value("\${scheduled.subject-access-request-processor.claim-threshold-mins:30}") private val claimThresholdMins: Long,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  init {
    log.info("SubjectAccessRequestService initiated claim threshold: {}mins", claimThresholdMins)
  }

  @Transactional
  fun findUnclaimed(): List<SubjectAccessRequest?> = subjectAccessRequestRepository.findUnclaimed(
    LocalDateTime.now().minusMinutes(claimThresholdMins),
  )

  @Transactional
  fun updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(
    id: UUID,
  ) = subjectAccessRequestRepository.updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(
    id,
    LocalDateTime.now().minusMinutes(claimThresholdMins),
    LocalDateTime.now(),
  )

  @Transactional
  fun updateStatus(id: UUID, status: Status) {
    val requestToUpdate =
      subjectAccessRequestRepository.findById(id)

    requestToUpdate.get().status = Status.Completed
    subjectAccessRequestRepository.save(requestToUpdate.get())
  }

  @Transactional
  fun validateAllServicesRendered(id: UUID) {
    subjectAccessRequestRepository.findById(id).ifPresentOrElse(
      { sar ->
        sar.servicesStillToRender().let { unrenderedServices ->
          if (unrenderedServices.isNotEmpty()) {
            throwRenderedAllServicesException(
              "Unable to process request as not all services were successfully rendered",
              sar,
              mapOf(
                "services" to unrenderedServices.associate { it.serviceConfiguration.serviceName to it.renderStatus },
              ),
            )
          }
        }
      },
      { throwRenderedAllServicesException("Subject access request with id $id not found") },
    )
  }

  @Transactional
  fun updateServiceStatusSuccess(id: UUID, serviceName: String, templateVersion: String?) {
    updateServiceStatus(id, serviceName, templateVersion, RenderStatus.COMPLETE)
  }

  @Transactional
  fun updateServiceStatusFailed(id: UUID, serviceName: String) {
    updateServiceStatus(id, serviceName, null, RenderStatus.ERRORED)
  }

  @Transactional
  fun updateServiceStatusSuspended(id: UUID, serviceName: String) {
    updateServiceStatus(id, serviceName, null, RenderStatus.SUSPENDED)
  }

  private fun updateServiceStatus(id: UUID, serviceName: String, templateVersion: String?, renderStatus: RenderStatus) {
    subjectAccessRequestRepository.findById(id).ifPresentOrElse(
      { sar: SubjectAccessRequest ->
        sar.services.find { it.serviceConfiguration.serviceName == serviceName }?.let { serviceDetail ->
          serviceDetail.renderStatus = renderStatus
          val versionInt = templateVersion?.toIntOrNull()
          if (serviceDetail.serviceConfiguration.templateMigrated && versionInt != null) {
            val templateVersionInstance = templateVersionRepository.findByServiceConfigurationIdAndVersion(
              serviceDetail.serviceConfiguration.id,
              versionInt,
            ) ?: throwUpdateServiceStatusException(
              "Could not find template version for service",
              sar,
              mapOf("version" to templateVersion, "service" to serviceName),
            )
            serviceDetail.templateVersion = templateVersionInstance
          }
        } ?: throwUpdateServiceStatusException(
          "Subject access request service not found",
          sar,
          mapOf("service" to serviceName),
        )
        subjectAccessRequestRepository.save(sar)
      },
      { throwUpdateServiceStatusException("Subject access request with id $id not found") },
    )
  }

  private fun throwUpdateServiceStatusException(
    message: String,
    subjectAccessRequest: SubjectAccessRequest? = null,
    params: Map<String, *>? = emptyMap<String, Any>(),
  ): Nothing = throw SubjectAccessRequestException(
    message = message,
    event = UPDATE_SAR_SERVICE_RENDER_STATUS,
    errorCode = ErrorCode.INTERNAL_SERVER_ERROR,
    subjectAccessRequest = subjectAccessRequest,
    params = params,
  )

  private fun throwRenderedAllServicesException(
    message: String,
    subjectAccessRequest: SubjectAccessRequest? = null,
    params: Map<String, *>? = emptyMap<String, Any>(),
  ): Nothing = throw SubjectAccessRequestException(
    message = message,
    event = GENERATE_REPORT_RENDER_ALL_COMPLETED,
    errorCode = SERVICES_NOT_RENDERED,
    subjectAccessRequest = subjectAccessRequest,
    params = params,
  )
}
