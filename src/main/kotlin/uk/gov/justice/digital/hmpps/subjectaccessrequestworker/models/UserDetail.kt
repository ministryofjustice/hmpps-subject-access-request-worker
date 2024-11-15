package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "USER_DETAIL")
data class UserDetail(

  @Id
  @Column(name = "username", nullable = false)
  val username: String,

  @Column(name = "last_name", nullable = false)
  val lastName: String,
) {
  constructor() : this("", "") {
  }
}
