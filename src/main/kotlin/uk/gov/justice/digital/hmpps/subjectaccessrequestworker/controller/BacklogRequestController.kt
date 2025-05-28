package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService
import java.net.URI
import java.time.LocalDate
import java.util.UUID

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
    val backlogRequest = BacklogRequest(createBacklogRequest)
    return backlogRequestService.newBacklogRequest(backlogRequest).let {
      ResponseEntity
        .created(URI("/subject-access-request-worker/backlog-request/${it.id}"))
        .body(BacklogResponseEntity(it))
    }
  }

  data class CreateBacklogRequest(
    val sarCaseReferenceId: String,
    val nomisId: String,
    val ndeliusCaseReferenceId: String,
    val dateFrom: LocalDate,
    val dateTo: LocalDate,
  )

  data class ServiceSummary(val serviceName: String, val status: String, val dataHeld: Boolean)

  data class BacklogResponseEntity(
    val sarCaseReferenceId: String,
    val id: UUID,
    val status: String,
    val serviceSummary: List<ServiceSummary>,
  ) {
    constructor(backlogRequest: BacklogRequest) : this(
      sarCaseReferenceId = backlogRequest.sarCaseReferenceNumber,
      id = backlogRequest.id,
      status = backlogRequest.status.name,
      serviceSummary = backlogRequest.serviceSummary.map {
        ServiceSummary(it.serviceName, it.status.name, it.dataHeld)
      },
    )
  }
}