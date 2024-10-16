package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import org.springframework.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.util.UUID

/**
 * A FatalSubjectAccessRequestException represents an error that requires manual intervention so shouldn't be retried.
 */
class FatalSubjectAccessRequestException : SubjectAccessRequestException {

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    status: HttpStatusCode,
    cause: Throwable,
  ) : super(
    event = event,
    subjectAccessRequestId = subjectAccessRequestId,
    message = FATAL_ERROR_MSG,
    httpStatusCode = status,
    cause = cause,
  )

  constructor(
    event: ProcessingEvent,
    id: UUID?,
    status: HttpStatusCode,
  ) : super(event, id, status, FATAL_ERROR_MSG)

  companion object {
    const val FATAL_ERROR_MSG = "subjectAccessRequest failed with non-retryable error"
  }
}
