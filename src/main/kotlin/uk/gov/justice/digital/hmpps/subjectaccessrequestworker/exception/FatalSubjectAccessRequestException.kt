package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

private const val FATAL_ERROR_MESSAGE_PREFIX = "subjectAccessRequest failed with non-retryable error: %s"

/**
 * A FatalSubjectAccessRequestException represents an error that requires manual intervention so shouldn't be retried.
 */
class FatalSubjectAccessRequestException(
  message: String,
  cause: Throwable?,
  event: ProcessingEvent,
  errorCode: ErrorCode,
  subjectAccessRequest: SubjectAccessRequest? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  FATAL_ERROR_MESSAGE_PREFIX.format(message),
  cause,
  event,
  errorCode,
  subjectAccessRequest,
  params,
) {

  constructor(
    message: String,
    event: ProcessingEvent,
    errorCode: ErrorCode,
    subjectAccessRequest: SubjectAccessRequest? = null,
    params: Map<String, *>? = null,
  ) : this(message, null, event, errorCode, subjectAccessRequest, params)
}
