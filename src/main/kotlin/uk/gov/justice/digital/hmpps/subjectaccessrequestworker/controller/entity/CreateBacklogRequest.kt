package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity

import com.fasterxml.jackson.annotation.JsonFormat
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class CreateBacklogRequest(
  val sarCaseReferenceId: String?,
  val nomisId: String? = null,
  val ndeliusCaseReferenceId: String? = null,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateFrom: LocalDate,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateTo: LocalDate,
)

data class BacklogResponseEntity(
  val id: UUID,
  val sarCaseReferenceId: String,
  val nomisId: String?,
  val ndeliusCaseReferenceId: String?,
  val status: String,
  val serviceSummary: List<ServiceSummary>,
  val createdDate: LocalDateTime?,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateFrom: LocalDate?,
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  val dateTo: LocalDate?,
) {
  constructor(backlogRequest: BacklogRequest) : this(
    id = backlogRequest.id,
    sarCaseReferenceId = backlogRequest.sarCaseReferenceNumber,
    nomisId = backlogRequest.nomisId,
    ndeliusCaseReferenceId = backlogRequest.ndeliusCaseReferenceId,
    status = backlogRequest.status.name,
    createdDate = backlogRequest.createdAt,
    serviceSummary = backlogRequest.serviceSummary.map {
      ServiceSummary(it.serviceName, it.status.name, it.dataHeld)
    },
    dateFrom = backlogRequest.dateFrom,
    dateTo = backlogRequest.dateTo,
  )
}

data class ServiceSummary(
  val serviceName: String,
  val processingStatus: String,
  val dataHeld: Boolean,
)

class BacklogRequestException(
  val backlogRequestId: UUID,
  msg: String,
  cause: Throwable?,
) : RuntimeException(msg, cause) {

  constructor(backlogRequestId: UUID, msg: String) : this(backlogRequestId, msg, null)
}
