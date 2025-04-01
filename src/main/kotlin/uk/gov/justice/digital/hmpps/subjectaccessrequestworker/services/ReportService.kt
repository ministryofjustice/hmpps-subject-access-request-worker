package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

interface ReportService {

  fun generateReport(subjectAccessRequest: SubjectAccessRequest)
}
