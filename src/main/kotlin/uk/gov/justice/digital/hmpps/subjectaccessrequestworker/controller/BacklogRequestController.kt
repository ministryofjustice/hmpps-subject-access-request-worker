package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller

import jakarta.validation.ValidationException
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogResponseEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService
import java.net.URI
import java.util.UUID

@ConditionalOnExpression("\${backlog-request.api.enabled:false}")
@RestController
@PreAuthorize("hasAnyRole('ROLE_SAR_SUPPORT')")
@RequestMapping(value = ["/subject-access-request/backlog"], produces = ["application/json"])
class BacklogRequestController(
  val backlogRequestService: BacklogRequestService,
) {

  @GetMapping
  fun getAllRequests(): List<BacklogResponseEntity> = backlogRequestService
    .getAllBacklogRequests()
    .map { BacklogResponseEntity(it) }

  @GetMapping(value = ["/{id}"])
  fun getRequestById(@PathVariable("id") id: UUID): ResponseEntity<BacklogResponseEntity> = backlogRequestService
    .getById(id)
    ?.let { ResponseEntity.ok(BacklogResponseEntity(it)) }
    ?: ResponseEntity.notFound().build()

  @PostMapping
  fun createBacklogRequest(@RequestBody createBacklogRequest: CreateBacklogRequest): ResponseEntity<BacklogResponseEntity> {
    validateRequest(createBacklogRequest)

    val backlogRequest = BacklogRequest(createBacklogRequest)
    return backlogRequestService.newBacklogRequest(backlogRequest).let {
      ResponseEntity
        .created(URI("/subject-access-request/backlog/${it.id}"))
        .body(BacklogResponseEntity(it))
    }
  }

  private fun validateRequest(createBacklogRequest: CreateBacklogRequest) {
    if (createBacklogRequest.sarCaseReferenceId.isNullOrBlank()) {
      throw ValidationException("non null/empty value is required for sarCaseReferenceId")
    }
    if (createBacklogRequest.nomisId.isNullOrEmpty() && createBacklogRequest.ndeliusCaseReferenceId.isNullOrEmpty()) {
      throw ValidationException("a non null/empty value is required for nomisId or ndeliusCaseReferenceId")
    }
  }
}
