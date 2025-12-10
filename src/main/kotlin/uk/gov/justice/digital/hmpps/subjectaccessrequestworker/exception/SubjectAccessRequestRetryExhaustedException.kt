package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

private const val ERROR_MESSAGE_PREFIX = "subjectAccessRequest failed and max retry attempts (%s) exhausted"

/**
 * A subject access request has failed with an error and has reached the max retry max limit.
 */
class SubjectAccessRequestRetryExhaustedException(
  retryAttempts: Long,
  cause: Throwable?,
  event: ProcessingEvent,
  errorCode: ErrorCode,
  subjectAccessRequest: SubjectAccessRequest? = null,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  ERROR_MESSAGE_PREFIX.format(retryAttempts),
  cause,
  event,
  errorCode,
  subjectAccessRequest,
  params,
) {

  constructor(
    retryAttempts: Long,
    event: ProcessingEvent,
    errorCode: ErrorCode,
    subjectAccessRequest: SubjectAccessRequest? = null,
    params: Map<String, *>? = null,
  ) : this(retryAttempts, null, event, errorCode, subjectAccessRequest, params)
}
