package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

class SubjectAccessRequestTemplatingException(
  subjectAccessRequest: SubjectAccessRequest?,
  message: String,
  errorCode: ErrorCode,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  subjectAccessRequest = subjectAccessRequest,
  message = message,
  event = ProcessingEvent.RENDER_TEMPLATE,
  errorCode = errorCode,
  params = params,
) {
  constructor(message: String, params: Map<String, *>? = null, errorCode: ErrorCode) : this(
    subjectAccessRequest = null,
    message = message,
    errorCode = errorCode,
    params = params,
  )
}
