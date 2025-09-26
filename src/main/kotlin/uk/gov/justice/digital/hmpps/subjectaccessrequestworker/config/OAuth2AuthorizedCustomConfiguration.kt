package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import uk.gov.justice.hmpps.kotlin.auth.service.GlobalPrincipalOAuth2AuthorizedClientService
import kotlin.apply as kotlinApply

/**
 * Class essentially duplicates the default functionality provided in the Kotlin-lib library but exposes the
 * oAuth2AuthorizedClientService as a Bean so can be accessed by classes/tests that need it - the
 * GlobalPrincipalOAuth2AuthorizedClientService instance created in the lib is created inline so it is not accessible
 * via @Autowire.
 */
@AutoConfigureAfter(OAuth2ClientWebSecurityAutoConfiguration::class)
@Configuration
class OAuth2AuthorizedCustomConfiguration {

  @Bean
  fun oAuth2AuthorizedClientService(
    clientRegistrationRepository: ClientRegistrationRepository,
  ): OAuth2AuthorizedClientService = GlobalPrincipalOAuth2AuthorizedClientService(clientRegistrationRepository)

  @Bean
  fun authorizedClientManager(
    oAuth2AuthorizedClientService: OAuth2AuthorizedClientService,
    clientRegistrationRepository: ClientRegistrationRepository,
    oAuth2AuthorizedClientProvider: OAuth2AuthorizedClientProvider,
  ): OAuth2AuthorizedClientManager = AuthorizedClientServiceOAuth2AuthorizedClientManager(
    clientRegistrationRepository,
    oAuth2AuthorizedClientService,
  ).kotlinApply {
    setAuthorizedClientProvider(oAuth2AuthorizedClientProvider)
  }
}
