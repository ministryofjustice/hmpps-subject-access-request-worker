package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.SubjectAccessRequestRepository
import java.time.LocalDateTime
import java.util.*

@Service
class SubjectAccessRequestService(
  private val subjectAccessRequestRepository: SubjectAccessRequestRepository,
) {

  @Transactional
  fun findUnclaimed(): List<SubjectAccessRequest?> =
    subjectAccessRequestRepository.findUnclaimed(LocalDateTime.now().minusMinutes(30))

  @Transactional
  fun updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(id: UUID) =
    subjectAccessRequestRepository.updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(
      id,
      LocalDateTime.now().minusMinutes(30),
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
