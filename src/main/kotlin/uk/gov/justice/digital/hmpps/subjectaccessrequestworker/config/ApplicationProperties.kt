package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "application")
data class ApplicationProperties(
  val serviceRenderer: ServiceRenderer,
)
