package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.client.ClientAuthorizationException
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase

abstract class BaseClientIntTest : IntegrationTestBase() {

  companion object {

    const val AUTH_ERROR_PREFIX =
      "[invalid_token_response] An error occurred while attempting to retrieve the OAuth 2.0 Access Token Response:"

    @JvmStatic
    fun status4xxResponseStubs(): List<StubErrorResponse> = listOf(
      StubErrorResponse(HttpStatus.BAD_REQUEST, WebClientRequestException::class.java),
      StubErrorResponse(HttpStatus.UNAUTHORIZED, WebClientRequestException::class.java),
      StubErrorResponse(HttpStatus.FORBIDDEN, WebClientRequestException::class.java),
      StubErrorResponse(HttpStatus.NOT_FOUND, WebClientRequestException::class.java),
    )

    @JvmStatic
    fun status5xxResponseStubs(): List<StubErrorResponse> = listOf(
      StubErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, WebClientResponseException.InternalServerError::class.java),
      StubErrorResponse(HttpStatus.BAD_GATEWAY, WebClientResponseException.BadGateway::class.java),
      StubErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, WebClientResponseException.ServiceUnavailable::class.java),
      StubErrorResponse(HttpStatus.GATEWAY_TIMEOUT, WebClientResponseException.GatewayTimeout::class.java),
    )

    @JvmStatic
    fun authErrorResponseStubs(): List<StubErrorResponse> = listOf(
      StubErrorResponse(HttpStatus.UNAUTHORIZED, ClientAuthorizationException::class.java),
      StubErrorResponse(HttpStatus.FORBIDDEN, ClientAuthorizationException::class.java),
      StubErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ClientAuthorizationException::class.java),
      StubErrorResponse(HttpStatus.BAD_GATEWAY, ClientAuthorizationException::class.java),
      StubErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, ClientAuthorizationException::class.java),
      StubErrorResponse(HttpStatus.GATEWAY_TIMEOUT, ClientAuthorizationException::class.java),
    )

    data class StubErrorResponse(val status: HttpStatus, val expectedException: Class<out Throwable>) {
      fun getResponse(): ResponseDefinitionBuilder = ResponseDefinitionBuilder()
        .withStatus(status.value())
        .withStatusMessage(status.reasonPhrase)
    }
  }
}
