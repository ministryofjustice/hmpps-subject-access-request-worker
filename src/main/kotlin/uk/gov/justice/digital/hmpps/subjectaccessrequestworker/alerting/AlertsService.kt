package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException

interface AlertsService {

  /**
   * Raises a Subject Access Request processing error notification.
   */
  fun raiseReportErrorAlert(ex: SubjectAccessRequestException)
}
