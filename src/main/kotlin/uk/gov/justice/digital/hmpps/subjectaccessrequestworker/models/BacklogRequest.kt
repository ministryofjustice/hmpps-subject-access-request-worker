package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

enum class BacklogRequestStatus {
  PENDING,
  COMPLETE,
}

@Entity
@Table(name = "sar_backlog_request")
data class BacklogRequest(
  @Id
  val id: UUID = UUID.randomUUID(),

  val dateFrom: LocalDate? = null,

  var dateTo: LocalDate? = null,

  val sarCaseReferenceNumber: String = "",

  val nomisId: String? = null,

  val ndeliusCaseReferenceId: String? = null,

  @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
  @JoinColumn(name = "backlog_request_id", referencedColumnName = "id")
  val serviceSummary: List<BacklogRequestServiceSummary>? = null,
) {
  constructor() : this(
    dateTo = null,
    dateFrom = null,
    nomisId = null,
    ndeliusCaseReferenceId = null,
  )
}


@Entity
@Table(name = "backlog_request_service_summary")
data class BacklogRequestServiceSummary(
  @Id
  val id: UUID = UUID.randomUUID(),

  val serviceName: String? = null,

  val dataHeld: Boolean = false,

  @Enumerated(EnumType.STRING)
  var status: BacklogRequestStatus = BacklogRequestStatus.PENDING,
) {
  constructor() : this(serviceName = null, dataHeld = false, status = BacklogRequestStatus.PENDING)
}
