package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.util.UUID

private const val ERROR_MESSAGE_PREFIX = "subjectAccessRequest failed and max retry attempts (%s) exhausted"

/**
 * A subject access request has failed with an error and has reached the max retry max limit.
 */
class SubjectAccessRequestRetryExhaustedException(
  retryAttempts: Long,
  cause: Throwable?,
  event: ProcessingEvent,
  subjectAccessRequestId: UUID? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(ERROR_MESSAGE_PREFIX.format(retryAttempts), cause, event, subjectAccessRequestId, params) {

  constructor(
    retryAttempts: Long,
    event: ProcessingEvent,
    subjectAccessRequestId: UUID? = null,
    params: Map<String, *>? = null,
  ) : this(retryAttempts, null, event, subjectAccessRequestId, params)
}
