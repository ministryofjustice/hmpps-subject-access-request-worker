package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.util.UUID

/**
 * A subject access request has failed with an error and reached the max retry max limit.
 */
class SubjectAccessRequestRetryExhaustedException(
  event: ProcessingEvent,
  subjectAccessRequestId: UUID?,
  cause: Throwable,
  retryAttempts: Long,
) : SubjectAccessRequestException(
  event,
  subjectAccessRequestId,
  "subjectAccessRequest failed and max retry attempts ($retryAttempts) exhausted",
  cause,
)
