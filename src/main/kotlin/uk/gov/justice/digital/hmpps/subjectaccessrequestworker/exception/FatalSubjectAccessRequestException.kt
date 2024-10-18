package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import org.springframework.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.net.URI
import java.util.UUID

private const val FATAL_ERROR_MESSAGE_PREFIX = "subjectAccessRequest failed with non-retryable error"

/**
 * A FatalSubjectAccessRequestException represents an error that requires manual intervention so shouldn't be retried.
 */
class FatalSubjectAccessRequestException : SubjectAccessRequestException {

  constructor(
    event: ProcessingEvent,
    id: UUID?,
    status: HttpStatusCode,
  ) : super(event, id, status, FATAL_ERROR_MESSAGE_PREFIX)

  constructor(
    event: ProcessingEvent,
    id: UUID?,
    url: URI,
    status: HttpStatusCode,
  ) : super(event, id, status, url, FATAL_ERROR_MESSAGE_PREFIX)
}
