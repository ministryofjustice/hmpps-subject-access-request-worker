package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "REQUEST_SERVICE_DETAIL")
data class RequestServiceDetail(
  @Id
  val id: UUID = UUID.randomUUID(),

  @ManyToOne
  @JoinColumn(name = "subject_access_request_id", referencedColumnName = "id", nullable = false)
  var subjectAccessRequest: SubjectAccessRequest,

  @OneToOne
  @JoinColumn(name = "service_configuration_id", referencedColumnName = "id", nullable = false)
  val serviceConfiguration: ServiceConfiguration,

  @Enumerated(EnumType.STRING)
  @Column(name = "render_status", nullable = false)
  var renderStatus: RenderStatus,

  @OneToOne
  @JoinColumn(name = "template_version_id", referencedColumnName = "id", nullable = false)
  var templateVersion: TemplateVersion? = null,

  @Column(name = "rendered_at")
  val renderedAt: LocalDateTime? = null,

) {

  /** Required to stop stackoverflow due to cyclic dependency caused by reference to parent subjectAccessRequest object. */
  override fun toString(): String = "RequestServiceDetail(id=$id, subjectAccessRequest=${subjectAccessRequest.id}, serviceConfiguration=$serviceConfiguration, renderStatus=$renderStatus, templateVersion=$templateVersion, renderedAt=$renderedAt)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as RequestServiceDetail

    if (id != other.id) return false
    if (subjectAccessRequest.id != other.subjectAccessRequest.id) return false
    if (serviceConfiguration != other.serviceConfiguration) return false
    if (renderStatus != other.renderStatus) return false
    if (templateVersion != other.templateVersion) return false
    if (renderedAt != other.renderedAt) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + subjectAccessRequest.id.hashCode()
    result = 31 * result + serviceConfiguration.hashCode()
    result = 31 * result + renderStatus.hashCode()
    result = 31 * result + (templateVersion?.hashCode() ?: 0)
    result = 31 * result + (renderedAt?.hashCode() ?: 0)
    return result
  }
}

enum class RenderStatus {
  PENDING,
  COMPLETE,
  ERRORED,
}
