package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import org.springframework.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.util.UUID

open class SubjectAccessRequestException : RuntimeException {

  constructor(message: String) : super(message)

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    message: String,
  ) : super(MSG_FORMAT_NO_STATUS.format(message, subjectAccessRequestId, event.name))

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    message: String,
    cause: Throwable,
  ) : super(MSG_FORMAT_NO_STATUS.format(message, subjectAccessRequestId, event.name), cause)

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    httpStatusCode: HttpStatusCode,
    message: String,
    cause: Throwable,
  ) : super(MSG_FORMAT_WITH_STATUS.format(message, subjectAccessRequestId, event.name, httpStatusCode), cause)

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    httpStatusCode: HttpStatusCode,
    message: String,
  ) : super(MSG_FORMAT_WITH_STATUS.format(message, subjectAccessRequestId, event.name, httpStatusCode))

  companion object {
    const val MSG_FORMAT_NO_STATUS = "%s, id=%s, event=%s"
    const val MSG_FORMAT_WITH_STATUS = "$MSG_FORMAT_NO_STATUS, httpStatus=%s"
  }
}
