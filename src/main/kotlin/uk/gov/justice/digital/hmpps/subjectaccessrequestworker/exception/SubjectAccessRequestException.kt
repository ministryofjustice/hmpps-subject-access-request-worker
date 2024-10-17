package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import org.springframework.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.net.URI
import java.util.UUID

open class SubjectAccessRequestException : RuntimeException {

  constructor(message: String) : super(message)

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    message: String,
  ) : super("%s, id=%s, event=%s".format(message, subjectAccessRequestId, event.name))

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    message: String,
    cause: Throwable,
  ) : super("%s, id=%s, event=%s".format(message, subjectAccessRequestId, event.name), cause)

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    httpStatusCode: HttpStatusCode,
    message: String,
    cause: Throwable,
  ) : super(
    "%s, id=%s, event=%s, httpStatus=%s".format(message, subjectAccessRequestId, event.name, httpStatusCode),
    cause,
  )

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    httpStatusCode: HttpStatusCode,
    message: String,
  ) : super("%s, id=%s, event=%s, httpStatus=%s".format(message, subjectAccessRequestId, event.name, httpStatusCode))

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    httpStatusCode: HttpStatusCode,
    uri: URI,
    message: String,
  ) : super(
    "%s, id=%s, event=%s, uri=%s, httpStatus=%s".format(
      message,
      subjectAccessRequestId,
      event.name,
      formatURI(uri),
      httpStatusCode,
    ),
  )

  constructor(
    event: ProcessingEvent,
    subjectAccessRequestId: UUID?,
    uri: URI,
    message: String,
    cause: Throwable,
  ) : super(
    "%s, id=%s, event=%s, uri=%s".format(
      message,
      subjectAccessRequestId,
      event.name,
      formatURI(uri),
    ),
    cause,
  )

  companion object {
    fun formatURI(uri: URI): String {
      return "${uri.scheme}://${uri.host}:${uri.port}${uri.path}"
    }
  }
}
