package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.retry.Retry.RetrySignal
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.defaultErrorCodeFor
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Service
class WebClientErrorResponseService {

  private val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

  fun getErrorCodeOrDefault(
    signal: RetrySignal,
    errorCodePrefix: ErrorCodePrefix,
  ): ErrorCode = getBodyAsErrorResponseOrNull(signal)?.errorCode.takeIf { !it.isNullOrBlank() }?.let {
    ErrorCode(errorCodePrefix, it)
  } ?: getDefaultErrorCode(errorCodePrefix, signal.failure())

  fun getBodyAsErrorResponseOrNull(signal: RetrySignal): ErrorResponse? = signal.failure()
    .takeIf { it is WebClientResponseException }
    ?.let { ex ->
      ex as WebClientResponseException
      val bodyString = ex.getResponseBodyAs(String::class.java)

      bodyString.takeIf { responseBodyIsJson(it, ex) }?.let {
        objectMapper.readValue(it, ErrorResponse::class.java)
      }
    }

  private fun responseBodyIsJson(
    body: String?,
    wcex: WebClientResponseException,
  ): Boolean = wcex.headers.contentType == MediaType.APPLICATION_JSON &&
    body != null &&
    body.startsWith("{") &&
    body.endsWith("}")

  private fun getDefaultErrorCode(
    errorCodePrefix: ErrorCodePrefix,
    failure: Throwable,
  ): ErrorCode = if (failure is WebClientResponseException) {
    ErrorCode(errorCodePrefix, failure.statusCode.value().toString())
  } else {
    defaultErrorCodeFor(errorCodePrefix)
  }
}
