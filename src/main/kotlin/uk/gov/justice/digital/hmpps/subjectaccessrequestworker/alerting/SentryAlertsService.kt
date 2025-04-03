package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting

import io.sentry.Sentry
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException

/**
 * Class added to create abstraction between static Sentry class and services calling it. Class doesn't add or change
 * functionality it just makes it easier to mock the alerting functionality in tests.
 */
@Service
class SentryAlertsService : AlertsService {

  override fun raiseReportErrorAlert(ex: SubjectAccessRequestException) {
    Sentry.captureException(ex)
  }
}
