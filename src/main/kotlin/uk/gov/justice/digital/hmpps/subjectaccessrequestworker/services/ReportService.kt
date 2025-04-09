package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

interface ReportService {

  suspend fun generateReport(subjectAccessRequest: SubjectAccessRequest)
}
