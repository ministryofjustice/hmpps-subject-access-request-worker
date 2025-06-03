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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.CreateBacklogRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class BacklogRequestStatus {
  PENDING,
  COMPLETE,
}

@Entity
@Table(name = "backlog_request")
data class BacklogRequest(
  @Id
  val id: UUID = UUID.randomUUID(),
  var sarCaseReferenceNumber: String = "",
  var nomisId: String? = null,
  var ndeliusCaseReferenceId: String? = null,
  @Enumerated(EnumType.STRING)
  var status: BacklogRequestStatus = BacklogRequestStatus.PENDING,
  var dateFrom: LocalDate? = null,
  var dateTo: LocalDate? = null,
  @OneToMany(mappedBy = "backlogRequest", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
  var serviceSummary: MutableList<ServiceSummary> = mutableListOf(),
  val claimDateTime: LocalDateTime? = null,
  val createdAt: LocalDateTime? = LocalDateTime.now(),
) {
  constructor() : this(
    dateTo = null,
    dateFrom = null,
    nomisId = null,
    ndeliusCaseReferenceId = null,
  )

  constructor(request: CreateBacklogRequest) : this(
    sarCaseReferenceNumber = request.sarCaseReferenceId ?: "",
    nomisId = if (request.nomisId.isNullOrEmpty()) null else request.nomisId,
    ndeliusCaseReferenceId = if (request.ndeliusCaseReferenceId.isNullOrEmpty()) null else request.ndeliusCaseReferenceId,
    dateTo = request.dateFrom,
    dateFrom = request.dateTo,
  )

  fun addServiceSummary(serviceSummary: ServiceSummary): BacklogRequest {
    this.serviceSummary.add(serviceSummary)
    return this
  }

  fun addServiceSummaries(vararg serviceSummaries: ServiceSummary): BacklogRequest {
    this.serviceSummary.addAll(serviceSummaries)
    return this
  }
}

@Entity
@Table(name = "service_summary")
data class ServiceSummary(
  @Id
  val id: UUID = UUID.randomUUID(),

  @ManyToOne
  @JoinColumn(name = "backlog_request_id", referencedColumnName = "id")
  var backlogRequest: BacklogRequest? = null,

  var serviceName: String,

  var dataHeld: Boolean = false,

  @Enumerated(EnumType.STRING)
  var status: BacklogRequestStatus = BacklogRequestStatus.PENDING,
) {
  constructor() : this(serviceName = "", dataHeld = false, status = BacklogRequestStatus.PENDING)

  /** Required to stop stackoverflow due to cyclic dependency caused by reference to parent backlogRequest object. */
  override fun toString(): String = "ServiceSummary(id=$id, backlogRequest=${backlogRequest?.id}, " +
    "serviceName='$serviceName', dataHeld=$dataHeld, status=$status)"
}
