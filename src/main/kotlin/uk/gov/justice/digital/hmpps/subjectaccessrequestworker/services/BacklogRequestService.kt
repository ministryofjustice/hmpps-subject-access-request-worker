package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import java.util.UUID

@Service
class BacklogRequestService(
  private val backlogRequestRepository: BacklogRequestRepository,
) {

  fun getAllBacklogRequests(): List<BacklogRequest> = backlogRequestRepository.findAll()

  fun getById(requestId: UUID): BacklogRequest? = backlogRequestRepository.findByIdOrNull(requestId)

  fun newBacklogRequest(request: BacklogRequest): BacklogRequest = try {
    backlogRequestRepository.save(request)
  } catch (ex: DataIntegrityViolationException) {
    throw BacklogRequestException("Could not create a new BacklogRequest: unique constraint violated", ex)
  }
}