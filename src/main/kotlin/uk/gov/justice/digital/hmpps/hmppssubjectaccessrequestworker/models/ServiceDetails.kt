package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models

class ServiceDetails(
  val url: String,
  val name: String,
  val orderPosition: Int? = null,
  val businessName: String? = null,
) {
}