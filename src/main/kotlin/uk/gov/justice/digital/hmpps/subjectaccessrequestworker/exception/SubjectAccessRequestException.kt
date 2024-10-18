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

  constructor(message: String): this(message, null, null, null)

  override val message: String?
    get() {
      val formattedParams = params?.entries?.joinToString(", ") { entry ->
        val second = if (entry.value is URI) {
          formatURI(entry.value as URI)
        } else {
          entry.value.toString()
        }
        "${entry.key}=${second}"
      }

      if (formattedParams.isNullOrEmpty()) {
        return "${super.message}, event=$event, id=$subjectAccessRequestId"
      }

      return "${super.message}, event=$event, id=$subjectAccessRequestId, $formattedParams"
    }

  private fun formatURI(uri: URI): String {
    return "${uri.scheme}://${uri.host}:${uri.port}${uri.path}"
  }

}

fun main(args: Array<String>) {
  throw SubjectAccessRequestException(
    "something happened",
    RuntimeException("boom"),
    ProcessingEvent.GET_SAR_DATA,
    UUID.randomUUID(),
    mapOf(
      "status" to 401,
      "url" to URI("http://localhost:8080/abc/123?a=b&c=d"),
    ),
  )
}
