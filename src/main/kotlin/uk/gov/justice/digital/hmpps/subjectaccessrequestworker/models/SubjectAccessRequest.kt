package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.ServiceConfigurationComparator
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

enum class Status {
  Pending,
  Completed,
}

@Entity
@Table(name = "SUBJECT_ACCESS_REQUEST")
data class SubjectAccessRequest(
  @Id
  val id: UUID = UUID.randomUUID(),
  @Enumerated(EnumType.STRING)
  var status: Status = Status.Pending,
  val dateFrom: LocalDate? = null,
  var dateTo: LocalDate? = null,
  val sarCaseReferenceNumber: String = "",
  @OneToMany(mappedBy = "subjectAccessRequest", cascade = [CascadeType.ALL], fetch = FetchType.EAGER, orphanRemoval = true)
  val services: MutableList<RequestServiceDetail> = mutableListOf(),
  val nomisId: String? = null,
  val ndeliusCaseReferenceId: String? = null,
  val requestedBy: String = "",
  val requestDateTime: LocalDateTime = LocalDateTime.now(),
  val claimDateTime: LocalDateTime? = null,
  val claimAttempts: Int = 0,
  val objectUrl: String? = null,
  val lastDownloaded: LocalDateTime? = null,
  /**
   * ContextId is unique ID specifically for logging, providing a mechanism to identify all events from 1 thread of
   * processing. If 2 workers are working on the same job it makes it possible to see which events belong together.
   **/
  @Transient
  val contextId: UUID? = UUID.randomUUID(),
) {
  fun servicesStillToRender() = this.services.filter { it.renderStatus != RenderStatus.COMPLETE }

  fun getSelectedServices(filter: (RequestServiceDetail) -> Boolean = { true }): List<ServiceConfiguration> = this.services
    .filter(filter).map { it.serviceConfiguration }.sortedWith(ServiceConfigurationComparator())
}
