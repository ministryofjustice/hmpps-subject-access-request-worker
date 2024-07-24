package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models

class ServiceConfig(
  val dpsServices: MutableList<DpsService> = mutableListOf(),
)
