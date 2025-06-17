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
  var subjectName: String = "",
  var version: String = "",
  var nomisId: String? = null,
  var ndeliusCaseReferenceId: String? = null,
  @Enumerated(EnumType.STRING)
  var status: BacklogRequestStatus = BacklogRequestStatus.PENDING,
  var dateFrom: LocalDate? = null,
  var dateTo: LocalDate? = null,
  var dataHeld: Boolean? = null,
  @OneToMany(mappedBy = "backlogRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
  var serviceSummary: MutableList<ServiceSummary> = mutableListOf(),
  val claimDateTime: LocalDateTime? = null,
  val createdAt: LocalDateTime? = LocalDateTime.now(),
  val completedAt: LocalDateTime? = null,
) {
  constructor() : this(
    dateTo = null,
    dateFrom = null,
    nomisId = null,
    ndeliusCaseReferenceId = null,
    dataHeld = null,
  )

  constructor(request: CreateBacklogRequest) : this(
    subjectName = request.subjectName?: "",
    version = request.version?: "",
    sarCaseReferenceNumber = request.sarCaseReferenceId ?: "",
    nomisId = if (request.nomisId.isNullOrEmpty()) null else request.nomisId,
    ndeliusCaseReferenceId = if (request.ndeliusCaseReferenceId.isNullOrEmpty()) null else request.ndeliusCaseReferenceId,
    dateTo = request.dateTo,
    dateFrom = request.dateFrom,
  )

  fun addServiceSummary(serviceSummary: ServiceSummary): BacklogRequest {
    this.serviceSummary.add(serviceSummary)
    return this
  }

  fun addServiceSummaries(vararg serviceSummaries: ServiceSummary): BacklogRequest {
    serviceSummaries.forEach { it.backlogRequest = this }
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

  var serviceOrder: Int = 0,

  var dataHeld: Boolean = false,

  @Enumerated(EnumType.STRING)
  var status: BacklogRequestStatus = BacklogRequestStatus.PENDING,
) {
  constructor() : this(serviceName = "", serviceOrder = 0, dataHeld = false, status = BacklogRequestStatus.PENDING)

  /** Required to stop stackoverflow due to cyclic dependency caused by reference to parent backlogRequest object. */
  override fun toString(): String = "ServiceSummary(id=$id, backlogRequest=${backlogRequest?.id}, " +
    "serviceName='$serviceName', serviceOrder=$serviceOrder, dataHeld=$dataHeld, status=$status)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ServiceSummary

    if (serviceOrder != other.serviceOrder) return false
    if (dataHeld != other.dataHeld) return false
    if (id != other.id) return false
    if (backlogRequest?.id != other.backlogRequest?.id) return false
    if (serviceName != other.serviceName) return false
    if (status != other.status) return false

    return true
  }

  override fun hashCode(): Int {
    var result = serviceOrder
    result = 31 * result + dataHeld.hashCode()
    result = 31 * result + id.hashCode()
    result = 31 * result + (backlogRequest?.id.hashCode() ?: 0)
    result = 31 * result + serviceName.hashCode()
    result = 31 * result + status.hashCode()
    return result
  }
}
