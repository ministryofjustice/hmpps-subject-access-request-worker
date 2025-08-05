package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity

import com.fasterxml.jackson.annotation.JsonFormat
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class CreateBacklogRequest(
  val subjectName: String? = null,
  val version: String? = null,
  val sarCaseReferenceNumber: String? = null,
  val nomisId: String? = null,
  val ndeliusCaseReferenceId: String? = null,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateFrom: LocalDate,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateTo: LocalDate,
)

data class BacklogRequestVersions(val versions: Set<String>)

/**
 * Top level overview of backlog request (omits service summary values).
 */
data class BacklogRequestOverview(
  val id: UUID,
  val subjectName: String,
  val version: String,
  val sarCaseReferenceNumber: String?,
  val nomisId: String?,
  val ndeliusCaseReferenceId: String? = null,
  val status: String,
  val createdDate: LocalDateTime?,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateFrom: LocalDate?,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateTo: LocalDate?,
  val serviceQueried: Int = 0,
) {
  constructor(backlogRequest: BacklogRequest) : this(
    id = backlogRequest.id,
    subjectName = backlogRequest.subjectName,
    version = backlogRequest.version,
    sarCaseReferenceNumber = backlogRequest.sarCaseReferenceNumber,
    nomisId = backlogRequest.nomisId,
    ndeliusCaseReferenceId = backlogRequest.ndeliusCaseReferenceId,
    status = backlogRequest.status.name,
    createdDate = backlogRequest.createdAt,
    dateFrom = backlogRequest.dateFrom,
    dateTo = backlogRequest.dateTo,
    serviceQueried = backlogRequest.serviceSummary.size,
  )
}

/**
 * Detailed overview of backlog request that includes service summaries.
 */
data class BacklogRequestDetailsEntity(
  val id: UUID,
  val subjectName: String,
  val version: String,
  val sarCaseReferenceNumber: String?,
  val nomisId: String?,
  val ndeliusCaseReferenceId: String? = null,
  val status: String,
  val createdDate: LocalDateTime?,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateFrom: LocalDate?,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateTo: LocalDate?,
  val dataHeld: Boolean? = null,
  val serviceSummary: List<ServiceSummary>,
) {
  constructor(backlogRequest: BacklogRequest) : this(
    id = backlogRequest.id,
    subjectName = backlogRequest.subjectName,
    version = backlogRequest.version,
    sarCaseReferenceNumber = backlogRequest.sarCaseReferenceNumber,
    nomisId = backlogRequest.nomisId,
    ndeliusCaseReferenceId = backlogRequest.ndeliusCaseReferenceId,
    status = backlogRequest.status.name,
    createdDate = backlogRequest.createdAt,
    dateFrom = backlogRequest.dateFrom,
    dateTo = backlogRequest.dateTo,
    serviceSummary = backlogRequest.serviceSummary.map {
      ServiceSummary(
        serviceName = it.serviceConfiguration?.serviceName ?: "unknown",
        processingStatus = it.status.name,
        dataHeld = it.dataHeld,
      )
    },
    dataHeld = backlogRequest.serviceSummary.firstOrNull { it.dataHeld }?.dataHeld ?: false,
  )
}

data class BacklogStatusEntity(
  val totalRequests: Long,
  val pendingRequests: Long,
  val completedRequests: Long,
  val completeRequestsWithDataHeld: Long,
  val status: String,
)

data class ServiceSummary(
  val serviceName: String,
  val processingStatus: String,
  val dataHeld: Boolean,
)

open class BacklogRequestException(
  val backlogRequestId: UUID,
  msg: String,
  cause: Throwable?,
) : RuntimeException(msg, cause) {

  constructor(backlogRequestId: UUID, msg: String) : this(backlogRequestId, msg, null)
}

class BacklogRequestAlreadyExistsException(
  request: BacklogRequest,
) : BacklogRequestException(
  request.id,
  "nomisId:${request.nomisId}, ndeliusCaseReferenceId=${request.ndeliusCaseReferenceId}, dateFrom=${request.dateFrom}, dateTo=${request.dateTo}",
  null,
)

data class BacklogRequestsDeletedEntity(val deleted: Int)
