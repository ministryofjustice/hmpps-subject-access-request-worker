package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

open class SubjectAccessRequestException(
  message: String,
  cause: Throwable? = null,
  val event: ProcessingEvent?,
  val subjectAccessRequest: SubjectAccessRequest? = null,
  val params: Map<String, *>? = null,
) : RuntimeException(message, cause) {

  constructor(message: String) : this(message, null, null, null)

  /**
   * Return the exception message with additional details.
   */
  override val message: String?
    get() = buildString {
      append(super.message)
      cause?.message?.let { append(", cause=$it") }
      append(", event=$event, id=${subjectAccessRequest?.id}, contextId=${subjectAccessRequest?.contextId}")
      params?.toFormattedString()?.let { append(", $it") }
    }

  private fun Map<String, *>.toFormattedString() = this.entries.joinToString(", ") { entry ->
    "${entry.key}=${entry.value}"
  }
}
