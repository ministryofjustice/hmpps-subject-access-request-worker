package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "TEMPLATE_VERSION")
data class TemplateVersion(

  @Id
  val id: UUID = UUID.randomUUID(),

  @ManyToOne
  @JoinColumn(name = "service_configuration_id")
  val serviceConfiguration: ServiceConfiguration? = null,

  @Column(name = "status", nullable = true)
  @Enumerated(EnumType.STRING)
  var status: TemplateVersionStatus? = null,

  @Column(name = "version", nullable = false)
  val version: Int,

  @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
  var createdAt: LocalDateTime = LocalDateTime.now(),

  @Column(name = "file_hash", nullable = false)
  var fileHash: String? = null,
) {
  constructor() : this(
    id = UUID.randomUUID(),
    serviceConfiguration = null,
    status = null,
    version = 0,
    createdAt = LocalDateTime.now(),
    fileHash = null,
  )
}

enum class TemplateVersionStatus {
  PUBLISHED,
  PENDING,
}
