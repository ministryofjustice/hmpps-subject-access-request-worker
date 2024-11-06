package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models

import java.time.LocalDate

data class ReportParameters(
  val services: List<DpsService>,
  val nomisId: String?,
  val ndeliusCaseReferenceId: String?,
  val sarCaseReferenceNumber: String,
  val subjectName: String,
  val dateFrom: LocalDate? = null,
  val dateTo: LocalDate? = null,
  val subjectAccessRequest: SubjectAccessRequest,
)
