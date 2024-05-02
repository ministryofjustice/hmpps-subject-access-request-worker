package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.config

import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.web.reactive.config.WebFluxConfigurer

@Configuration
class WebFluxConfiguration : WebFluxConfigurer {
  override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
    configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)
  }
}
