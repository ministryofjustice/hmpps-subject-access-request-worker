package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

/**
 * TelemetryClient gets altered at runtime by the java agent and so is a no-op otherwise
 */
@Configuration
class ApplicationInsightsConfiguration {
  @Bean
  fun telemetryClient(): TelemetryClient = TelemetryClient()
}

const val UNKNOWN_PLACEHOLDER = "unknown"

fun TelemetryClient.trackEvent(name: String, properties: Map<String, String>) = this.trackEvent(name, properties, null)

fun TelemetryClient.trackSarEvent(
  name: String,
  subjectAccessRequest: SubjectAccessRequest?,
  vararg kvpairs: Pair<String, String>,
) {
  val id = subjectAccessRequest?.sarCaseReferenceNumber ?: UNKNOWN_PLACEHOLDER
  val contextId = subjectAccessRequest?.contextId.toString()

  this.trackEvent(
    name,
    mapOf(
      "sarId" to id,
      "UUID" to subjectAccessRequest?.id.toString(),
      "contextId" to contextId,
      *kvpairs,
    ),
    null,
  )
}
