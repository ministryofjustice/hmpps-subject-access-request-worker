package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.ValidationException
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestAlreadyExistsException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestDetailsEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestOverview
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestVersions
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestsDeletedEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogStatusEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestReportService
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
  val backlogRequestReportService: BacklogRequestReportService,
) {

  @Operation(
    summary = "Get a list of available backlog request versions",
    description = "Get a list of available backlog request versions",
    security = [SecurityRequirement(name = "ROLE_SAR_SUPPORT")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Get a list of available backlog request versions",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = BacklogRequestVersions::class),
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
  @GetMapping("/versions")
  fun getBacklogVersions(): ResponseEntity<BacklogRequestVersions> = ResponseEntity.ok(
    BacklogRequestVersions(
      backlogRequestService.getVersions(),
    ),
  )

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
      ApiResponse(
        responseCode = "404",
        description = "Version not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @GetMapping(value = ["/versions/{version}"])
  fun getBacklogVersionStatus(
    @PathVariable("version") version: String,
  ): ResponseEntity<BacklogStatusEntity> = backlogRequestService.getStatusByVersion(version)
    ?.let { ResponseEntity.ok(it.toBacklogStatusEntity()) }
    ?: ResponseEntity.notFound().build()

  @Operation(
    summary = "Delete backlog requests with the specified version",
    description = "Delete backlog requests with the specified version",
    security = [SecurityRequirement(name = "ROLE_SAR_SUPPORT")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Deleted backlog requests with the specified version",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = BacklogRequestsDeletedEntity::class),
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
      ApiResponse(
        responseCode = "404",
        description = "Version not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @DeleteMapping("/versions/{version}")
  fun deleteRequestByVersion(
    @PathVariable("version") version: String,
  ): ResponseEntity<BacklogRequestsDeletedEntity> = backlogRequestService.deleteByVersion(version)
    .takeIf { it > 0 }
    ?.let { ResponseEntity.ok(BacklogRequestsDeletedEntity(it)) }
    ?: ResponseEntity.notFound().build()

  @Operation(
    summary = "Get the list of backlog requests for the specified version",
    description = "Get the list of backlog requests for the specified version",
    security = [SecurityRequirement(name = "ROLE_SAR_SUPPORT")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "List of backlog requests for the specified version",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = BacklogRequestOverview::class)),
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
  @GetMapping("/versions/{version}/requests")
  fun getAllRequests(
    @PathVariable("version") version: String,
  ): ResponseEntity<List<BacklogRequestOverview>> = backlogRequestService.getRequestsForVersion(version)
    .takeIf { it.isNotEmpty() }
    ?.let {
      ResponseEntity.ok(
        it.map { request -> BacklogRequestOverview(request) },
      )
    }
    ?: ResponseEntity.notFound().build()

  @Operation(
    summary = "Generate a CSV report for all backlog requests in the specified version",
    description = "Generate a CSV report for all backlog requests in the specified version",
    security = [SecurityRequirement(name = "ROLE_SAR_SUPPORT")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "CSV report for all backlog requests in the specified version",
        content = [
          Content(
            mediaType = "text/csv",
            schema = Schema(type = "string", format = "binary"),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad request, the requested version does not have status COMPLETE",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
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
      ApiResponse(
        responseCode = "404",
        description = "Requested Version Not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @GetMapping("/versions/{version}/report", produces = ["text/csv"])
  fun generateBacklogRequestCsvReport(
    @PathVariable version: String,
    response: HttpServletResponse,
  ): Unit = backlogRequestReportService.generateReportJdbc(version, response)

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
            schema = Schema(implementation = BacklogRequestDetailsEntity::class),
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
  fun getRequestById(@PathVariable("id") id: UUID): ResponseEntity<BacklogRequestDetailsEntity> = backlogRequestService
    .getByIdOrNull(id)
    ?.let { ResponseEntity.ok(BacklogRequestDetailsEntity(it)) } ?: ResponseEntity.notFound().build()

  @Operation(
    summary = "Delete backlog requests by ID",
    description = "Delete backlog requests by ID",
    security = [SecurityRequirement(name = "ROLE_SAR_SUPPORT")],
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Deleted backlog requests by ID",
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
      ApiResponse(
        responseCode = "404",
        description = "Request not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @DeleteMapping(value = ["/{id}"])
  fun deleteRequestById(@PathVariable("id") id: UUID): ResponseEntity<Void> = backlogRequestService.getByIdOrNull(id)
    ?.let {
      backlogRequestService.deleteById(id)
      ResponseEntity.ok().build()
    } ?: ResponseEntity.notFound().build()

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
            schema = Schema(implementation = BacklogRequestDetailsEntity::class),
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
  fun createBacklogRequest(
    @RequestBody createBacklogRequest: CreateBacklogRequest,
  ): ResponseEntity<BacklogRequestOverview> {
    validateRequest(createBacklogRequest)

    return BacklogRequest(createBacklogRequest)
      .let { backlogRequest ->
        backlogRequestService.backlogRequestAlreadyExist(backlogRequest)
          .takeIf { it }
          ?.let { throw BacklogRequestAlreadyExistsException(backlogRequest) }
          ?: backlogRequestService.newBacklogRequest(backlogRequest).let {
            ResponseEntity
              .created(URI("/subject-access-request/backlog/${it.id}"))
              .body(BacklogRequestOverview(it))
          }
      }
  }

  private fun BacklogRequestService.BacklogStatus.toBacklogStatusEntity(): BacklogStatusEntity = BacklogStatusEntity(
    totalRequests = this.totalRequests,
    pendingRequests = this.pendingRequests,
    completedRequests = this.completedRequests,
    completeRequestsWithDataHeld = this.completeRequestsWithDataHeld,
    status = this.status.name,
  )

  private fun validateRequest(request: CreateBacklogRequest) {
    if (request.sarCaseReferenceNumber.isNullOrBlank()) {
      throw ValidationException("non null/empty value is required for sarCaseReferenceNumber")
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
