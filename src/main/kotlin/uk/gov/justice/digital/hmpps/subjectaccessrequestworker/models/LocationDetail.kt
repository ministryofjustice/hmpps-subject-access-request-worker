package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "LOCATION_DETAIL")
data class LocationDetail(

  @Id
  @Column(name = "dps_id", nullable = false)
  val dpsId: String,

  @Column(name = "nomis_id")
  val nomisId: Int?,

  @Column(name = "name", nullable = false)
  val name: String,
) {
  constructor() : this("", null, "")
}
