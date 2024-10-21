package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import java.net.URI
import java.util.UUID

open class SubjectAccessRequestException(
  message: String,
  cause: Throwable? = null,
  private val event: ProcessingEvent?,
  private val subjectAccessRequestId: UUID? = null,
  private val params: Map<String, *>? = null,
) : RuntimeException(message, cause) {

  constructor(message: String) : this(message, null, null, null)

  override val message: String?
    get() {
      val formattedParams = params?.toFormattedString()
      if (formattedParams.isNullOrEmpty()) {
        return "${super.message}, event=$event, id=$subjectAccessRequestId"
      }

      return "${super.message}, event=$event, id=$subjectAccessRequestId, $formattedParams"
    }

  private fun Map<String, *>.toFormattedString() = this.entries.joinToString(", ") { entry ->
    val value = entry.value
    val second = if (value is URI) {
      // Return URI without the query string
      "${value.scheme}://${value.host}:${value.port}${value.path}"
    } else {
      entry.value.toString()
    }
    "${entry.key}=$second"
  }
}
