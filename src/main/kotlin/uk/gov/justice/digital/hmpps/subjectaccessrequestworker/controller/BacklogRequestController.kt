package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogStatusEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.net.URI
import java.util.UUID

@ConditionalOnExpression("\${backlog-request.api.enabled:false}")
@RestController
@PreAuthorize("hasAnyRole('ROLE_SAR_SUPPORT')")
@RequestMapping(value = ["/subject-access-request/backlog"], produces = ["application/json"])
class BacklogRequestController(
  val backlogRequestService: BacklogRequestService,
) {

  @Operation(
    summary = "Get the list of backlog requests",
    description = "Get the list of backlog requests",
    security = [SecurityRequirement(name = "ROLE_SAR_SUPPORT")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The list of backlog requests currently stored",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = BacklogResponseEntity::class)),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @GetMapping
  fun getAllRequests(): List<BacklogResponseEntity> = backlogRequestService
    .getAllBacklogRequests()
    .map { BacklogResponseEntity(it) }

  @Operation(
    summary = "Get backlog request by ID",
    description = "Get backlog request by ID",
    security = [SecurityRequirement(name = "ROLE_SAR_SUPPORT")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Get backlog request by ID",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = BacklogResponseEntity::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @GetMapping(value = ["/{id}"])
  fun getRequestById(@PathVariable("id") id: UUID): ResponseEntity<BacklogResponseEntity> = backlogRequestService
    .getByIdOrNull(id)
    ?.let { ResponseEntity.ok(BacklogResponseEntity(it)) }
    ?: ResponseEntity.notFound().build()

  @Operation(
    summary = "Create a new backlog request",
    description = "Create a new backlog request",
    security = [SecurityRequirement(name = "ROLE_SAR_SUPPORT")],
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Backlog request created successfully",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = BacklogResponseEntity::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
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

  @Operation(
    summary = "Get status snapshot of backlog requests",
    description = "Get status snapshot of backlog requests",
    security = [SecurityRequirement(name = "ROLE_SAR_SUPPORT")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Get status snapshot of backlog requests",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = BacklogRequestStatus::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @GetMapping(value = ["/status"])
  fun getBacklogStatus(): ResponseEntity<BacklogStatusEntity> = ResponseEntity.ok(
    backlogRequestService.getStatus().toBacklogStatusEntity(),
  )

  fun BacklogRequestService.BacklogStatus.toBacklogStatusEntity(): BacklogStatusEntity = BacklogStatusEntity(
    totalRequests = this.totalRequests,
    pendingRequests = this.pendingRequests,
    completedRequests = this.completedRequests,
    completeRequestsWithDataHeld = this.completeRequestsWithDataHeld,
  )

  private fun validateRequest(request: CreateBacklogRequest) {
    if (request.sarCaseReferenceId.isNullOrBlank()) {
      throw ValidationException("non null/empty value is required for sarCaseReferenceId")
    }
    if (request.nomisId.isNullOrEmpty() && request.ndeliusCaseReferenceId.isNullOrEmpty()) {
      throw ValidationException("a non null/empty value is required for nomisId or ndeliusCaseReferenceId")
    }
    if (!request.nomisId.isNullOrEmpty() && !request.ndeliusCaseReferenceId.isNullOrEmpty()) {
      throw ValidationException("multiple ID's provided provided please provide either a nomisId or ndeliusCaseReferenceId")
    }
    if (request.subjectName.isNullOrEmpty()) {
      throw ValidationException("a non null/empty value is required for subject name")
    }
    if (request.version.isNullOrEmpty()) {
      throw ValidationException("a non null/empty value is required for version")
    }
  }
}
