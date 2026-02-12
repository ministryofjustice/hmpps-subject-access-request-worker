package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "SERVICE_CONFIGURATION")
data class ServiceConfiguration(
  @Id
  val id: UUID = UUID.randomUUID(),

  @Column(name = "service_name", nullable = false)
  val serviceName: String,

  @Column(name = "label", nullable = false)
  val label: String,

  @Column(name = "url", nullable = false)
  val url: String,

  @Column(name = "enabled", nullable = false)
  var enabled: Boolean,

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  val category: ServiceCategory,
) {
  constructor() : this(serviceName = "", label = "", url = "", enabled = false, category = ServiceCategory.PRISON)
}

enum class ServiceCategory {
  PRISON,
  PROBATION,
}
