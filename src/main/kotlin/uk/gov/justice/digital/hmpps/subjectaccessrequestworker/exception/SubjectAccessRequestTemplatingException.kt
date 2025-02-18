package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

class SubjectAccessRequestTemplatingException(
  subjectAccessRequest: SubjectAccessRequest?,
  message: String,
  params: Map<String, *>? = null,
) : SubjectAccessRequestException(
  subjectAccessRequest = subjectAccessRequest,
  message = message,
  event = ProcessingEvent.RENDER_TEMPLATE,
  params = params,
) {
  constructor(message: String, params: Map<String, *>? = null) : this(
    subjectAccessRequest = null,
    message = message,
    params = params,
  )
}
