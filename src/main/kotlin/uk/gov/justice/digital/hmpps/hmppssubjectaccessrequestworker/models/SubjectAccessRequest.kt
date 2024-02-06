package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestapi.models

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDate
import java.time.LocalDateTime

enum class Status {
  Pending,
  Completed,
}

@Entity
data class SubjectAccessRequest(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Int? = null,
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