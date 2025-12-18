package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.SubjectAccessRequestRepository
import java.time.LocalDateTime
import java.util.UUID

@Service
class SubjectAccessRequestService(
  private val subjectAccessRequestRepository: SubjectAccessRequestRepository,
  @Value("\${scheduled.subject-access-request-processor.claim-threshold-mins:30}") private val claimThresholdMins: Long,
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
}
