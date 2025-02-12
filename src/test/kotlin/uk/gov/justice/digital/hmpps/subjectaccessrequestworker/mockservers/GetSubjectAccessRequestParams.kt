package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers

import java.time.LocalDate

data class GetSubjectAccessRequestParams(
  val prn: String? = null,
  val crn: String? = null,
  val dateFrom: LocalDate? = null,
  val dateTo: LocalDate? = null,
)
