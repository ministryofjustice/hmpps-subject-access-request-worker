package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.springframework.http.HttpStatusCode
import java.util.UUID

class SubjectAccessRequestException : RuntimeException {
  constructor() : super()
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)

  companion object {
    fun claimRequestFailedException(id: UUID, status: HttpStatusCode): SubjectAccessRequestException =
      SubjectAccessRequestException(
        "subjectAccessRequest claim request $id failed with status code: $status",
      )

    fun claimRequestRetryExhaustedException(
      id: UUID,
      cause: Throwable,
      attempts: Long,
    ): SubjectAccessRequestException =
      SubjectAccessRequestException("claim request $id retry attempts ($attempts) exhausted", cause)
  }
}