package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.util.UUID

open class SubjectAccessRequestException(
  message: String,
  cause: Throwable? = null,
  private val event: ProcessingEvent?,
  private val subjectAccessRequestId: UUID? = null,
  private val params: Map<String, *>? = null,
) : RuntimeException(message, cause) {

  constructor(message: String) : this(message, null, null, null)

  /**
   * Return the exception message with additional details.
   */
  override val message: String?
    get() = buildString {
      append(super.message)
      cause?.message?.let { append(", cause=$it") }
      append(", event=$event, id=$subjectAccessRequestId")
      params?.toFormattedString()?.let { append(", $it") }
    }

  private fun Map<String, *>.toFormattedString() = this.entries.joinToString(", ") { entry ->
    "${entry.key}=${entry.value}"
  }
}
