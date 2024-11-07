package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate

data class PdfParameters(
  val services: List<DpsService>,
  val nomisId: String?,
  val ndeliusCaseReferenceId: String?,
  val sarCaseReferenceNumber: String,
  val subjectName: String,
  val dateFrom: LocalDate? = null,
  val dateTo: LocalDate? = null,
  val subjectAccessRequest: SubjectAccessRequest,
)
