package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import java.util.UUID

@Service
class BacklogRequestService(
  private val backlogRequestRepository: BacklogRequestRepository,
) {

  fun getAllBacklogRequests(): List<BacklogRequest> = backlogRequestRepository.findAll()

  fun getById(requestId: UUID): BacklogRequest? = backlogRequestRepository.findByIdOrNull(requestId)

  fun newBacklogRequest(request: BacklogRequest): BacklogRequest = backlogRequestRepository.save(request)
}