package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import org.springframework.http.HttpStatusCode

class ErrorCode(val code: String, private val prefix: ErrorCodePrefix) {

  constructor(status: HttpStatusCode, prefix: ErrorCodePrefix) : this(status.value().toString(), prefix) {}

  fun getCodeStr(): String = "${prefix}_$code"

  companion object {
    private const val DEFAULT_CODE = "1000"

    val INTERNAL_SERVER_ERROR = ErrorCode("10000", ErrorCodePrefix.WORKER_ERROR_PREFIX)
    val CONFIGURATION_ERROR = ErrorCode("10001", ErrorCodePrefix.WORKER_ERROR_PREFIX)

    val NOMIS_API_AUTH_ERROR = ErrorCode("20000", ErrorCodePrefix.NOMIS_API_ERROR_PREFIX)
    val DOCUMENT_STORE_AUTH_ERROR = ErrorCode("20001", ErrorCodePrefix.WORKER_ERROR_PREFIX)
    val HTML_RENDERER_AUTH_ERROR = ErrorCode("20002", ErrorCodePrefix.HTML_RENDERER_ERROR_PREFIX)
    val PRISON_API_AUTH_ERROR = ErrorCode("20003", ErrorCodePrefix.PRISON_API_ERROR_PREFIX)
    val LOCATION_API_AUTH_ERROR = ErrorCode("20004", ErrorCodePrefix.LOCATION_API_ERROR_PREFIX)
    val PROBATION_API_AUTH_ERROR = ErrorCode("20005", ErrorCodePrefix.PROBATION_API_ERROR_PREFIX)

    val DOCUMENT_STORE_ERROR = ErrorCode("30000", ErrorCodePrefix.WORKER_ERROR_PREFIX)
    val DOCUMENT_STORE_CONFLICT = ErrorCode("30001", ErrorCodePrefix.WORKER_ERROR_PREFIX)

    val TEMPLATE_NOT_FOUND = ErrorCode("40000", ErrorCodePrefix.WORKER_ERROR_PREFIX)
    val TEMPLATE_HELPER_ERROR = ErrorCode("40001", ErrorCodePrefix.WORKER_ERROR_PREFIX)

    val S3_GET_ERROR = ErrorCode("50000", ErrorCodePrefix.WORKER_ERROR_PREFIX)
    val S3_LIST_ERROR = ErrorCode("50001", ErrorCodePrefix.WORKER_ERROR_PREFIX)
    val S3_HEAD_OBJECT_ERROR = ErrorCode("50002", ErrorCodePrefix.WORKER_ERROR_PREFIX)

    fun defaultErrorCodeFor(prefix: ErrorCodePrefix): ErrorCode = ErrorCode(DEFAULT_CODE, prefix)
  }
}

enum class ErrorCodePrefix(private val prefix: String) {
  WORKER_ERROR_PREFIX("worker"),

  HTML_RENDERER_ERROR_PREFIX("renderer"),

  GOTENBERG_ERROR_PREFIX("gotenberg"),

  DOCUMENT_STORE_ERROR_PREFIX("document_store"),

  LOCATION_API_ERROR_PREFIX("location_api"),

  NOMIS_API_ERROR_PREFIX("nomis_api"),

  PRISON_API_ERROR_PREFIX("prison_api"),

  PROBATION_API_ERROR_PREFIX("probation_api"),
}
