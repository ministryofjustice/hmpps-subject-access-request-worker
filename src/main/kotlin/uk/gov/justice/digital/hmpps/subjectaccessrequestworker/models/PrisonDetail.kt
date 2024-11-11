package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "PRISON_DETAIL")
data class PrisonDetail(

  @Id
  @Column(name = "prison_id", nullable = false)
  val prisonId: String,

  @Column(name = "prison_name", nullable = false)
  val prisonName: String,
) {
  constructor() : this("", "") {
  }
}
