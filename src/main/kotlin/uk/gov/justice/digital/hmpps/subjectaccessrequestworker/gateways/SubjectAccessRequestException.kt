package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways

import org.springframework.http.HttpStatusCode
import java.util.UUID

class SubjectAccessRequestException : RuntimeException {
  constructor() : super()
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)

  companion object {
    fun claimSubjectAccessRequestFailedException(id: UUID, status: HttpStatusCode) =
      SubjectAccessRequestException(
        "subjectAccessRequest claim request $id failed with non-retryable error, status code: $status",
      )

    fun claimSubjectAccessRequestRetryExhaustedException(
      id: UUID,
      cause: Throwable,
      attempts: Long,
    ) = SubjectAccessRequestException(
      "claim subjectAccessRequest $id failed and retry attempts ($attempts) exhausted",
      cause,
    )

    fun completeSubjectRequestRetryExhaustedException(
      id: UUID,
      cause: Throwable,
      attempts: Long,
    ) = SubjectAccessRequestException(
      "complete subjectAccessRequest $id failed and retry attempts ($attempts) exhausted",
      cause,
    )

    fun completeSubjectAccessRequestFailedException(id: UUID, status: HttpStatusCode) =
      SubjectAccessRequestException(
        "subjectAccessRequest complete request $id failed with non-retryable error, status code: $status",
      )
  }
}
