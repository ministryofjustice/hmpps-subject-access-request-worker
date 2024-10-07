package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

class ServiceConfig(
  val dpsServices: MutableList<DpsService> = mutableListOf(),
)
