package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase

class BaseClientIntTest : IntegrationTestBase() {

  companion object {

    const val AUTH_ERROR_PREFIX =
      "[invalid_token_response] An error occurred while attempting to retrieve the OAuth 2.0 Access Token Response:"

    @JvmStatic
    fun status4xxResponseStubs(): List<StubErrorResponse> = listOf(
      StubErrorResponse(HttpStatus.BAD_REQUEST),
      StubErrorResponse(HttpStatus.UNAUTHORIZED),
      StubErrorResponse(HttpStatus.FORBIDDEN),
      StubErrorResponse(HttpStatus.NOT_FOUND),
    )

    @JvmStatic
    fun status5xxResponseStubs(): List<StubErrorResponse> = listOf(
      StubErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR),
      StubErrorResponse(HttpStatus.BAD_GATEWAY),
      StubErrorResponse(HttpStatus.SERVICE_UNAVAILABLE),
      StubErrorResponse(HttpStatus.GATEWAY_TIMEOUT),
    )

    @JvmStatic
    fun authErrorResponseStubs(): List<StubErrorResponse> = listOf(
      StubErrorResponse(HttpStatus.UNAUTHORIZED),
      StubErrorResponse(HttpStatus.FORBIDDEN),
      StubErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR),
      StubErrorResponse(HttpStatus.BAD_GATEWAY),
      StubErrorResponse(HttpStatus.SERVICE_UNAVAILABLE),
      StubErrorResponse(HttpStatus.GATEWAY_TIMEOUT),
    )

    data class StubErrorResponse(val status: HttpStatus) {
      fun getResponse(): ResponseDefinitionBuilder = ResponseDefinitionBuilder()
        .withStatus(status.value())
        .withStatusMessage(status.reasonPhrase)
    }
  }
}
