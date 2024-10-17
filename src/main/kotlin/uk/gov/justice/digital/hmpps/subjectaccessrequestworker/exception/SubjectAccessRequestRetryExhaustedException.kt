package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.net.URI
import java.util.UUID

private const val ERROR_MESSAGE_PREFIX = "subjectAccessRequest failed and max retry attempts (%s) exhausted"

/**
 * A subject access request has failed with an error and reached the max retry max limit.
 */
class SubjectAccessRequestRetryExhaustedException : SubjectAccessRequestException {
  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    cause: Throwable,
    retryAttempts: Long,
  ) : super(
    event,
    subjectAccessRequestId,
    ERROR_MESSAGE_PREFIX.format(retryAttempts),
    cause,
  )

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    uri: URI,
    cause: Throwable,
    retryAttempts: Long,
  ) : super(
    event,
    subjectAccessRequestId,
    uri,
    ERROR_MESSAGE_PREFIX.format(retryAttempts),
    cause,
  )
}
