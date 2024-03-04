package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class Status {
  Pending,
  Completed,
}

@Entity
data class SubjectAccessRequest(
  @Id
  val id: UUID = UUID.randomUUID(),
  @Enumerated(EnumType.STRING)
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
)
