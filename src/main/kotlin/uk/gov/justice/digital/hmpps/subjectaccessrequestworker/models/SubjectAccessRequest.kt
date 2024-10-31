package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class Status {
  Pending,
  Completed,
}

data class SubjectAccessRequest(
  val id: UUID = UUID.randomUUID(),
  val status: Status = Status.Pending,
  val dateFrom: LocalDate? = null,
  val dateTo: LocalDate? = null,
  val sarCaseReferenceNumber: String = "",
  val services: String = "",
  val nomisId: String? = null,
  val ndeliusCaseReferenceId: String? = null,
  val requestedBy: String = "",
  val requestDateTime: LocalDateTime = LocalDateTime.now(),
  val claimDateTime: LocalDateTime? = null,
  val claimAttempts: Int = 0,
  val objectUrl: String? = null,
  val lastDownloaded: LocalDateTime? = null,
)
