package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode

import org.springframework.http.HttpStatusCode

open class ErrorCode(
  val code: String,
  private val errorPrefix: ErrorCodePrefix,
) {

  constructor(
    status: HttpStatusCode,
    errorPrefix: ErrorCodePrefix,
  ) : this(status.value().toString(), errorPrefix)

  fun getCodeStr(): String = "${errorPrefix.prefix}_$code"

  override fun toString(): String = getCodeStr()

  override fun equals(other: Any?): Boolean = other is ErrorCode &&
    other.code == this.code &&
    other.errorPrefix == this.errorPrefix

  override fun hashCode(): Int {
    var result = code.hashCode()
    result = 31 * result + errorPrefix.hashCode()
    return result
  }

  companion object {
    const val DEFAULT_ERROR_CODE = "99999"

    val INTERNAL_SERVER_ERROR = ErrorCode("10000", ErrorCodePrefix.SAR_WORKER)

    val CONFIGURATION_ERROR = ErrorCode("10001", ErrorCodePrefix.SAR_WORKER)

    val NOMIS_API_AUTH_ERROR = ErrorCode("20000", ErrorCodePrefix.SAR_WORKER)

    val DOCUMENT_STORE_AUTH_ERROR = ErrorCode("20001", ErrorCodePrefix.SAR_WORKER)

    val HTML_RENDERER_AUTH_ERROR = ErrorCode("20002", ErrorCodePrefix.SAR_HTML_RENDERER)

    val PRISON_API_AUTH_ERROR = ErrorCode("20003", ErrorCodePrefix.PRISON_API)

    val LOCATION_API_AUTH_ERROR = ErrorCode("20004", ErrorCodePrefix.LOCATION_API)

    val PROBATION_API_AUTH_ERROR = ErrorCode("20005", ErrorCodePrefix.PROBATION_API)

    val DOCUMENT_UPLOAD_VERIFICATION_ERROR = ErrorCode("30000", ErrorCodePrefix.SAR_WORKER)

    val DOCUMENT_STORE_CONFLICT = ErrorCode("30001", ErrorCodePrefix.SAR_WORKER)

    val TEMPLATE_NOT_FOUND = ErrorCode("40000", ErrorCodePrefix.SAR_WORKER)

    val TEMPLATE_HELPER_ERROR = ErrorCode("40001", ErrorCodePrefix.SAR_WORKER)

    val S3_GET_ERROR = ErrorCode("50000", ErrorCodePrefix.SAR_WORKER)

    val S3_LIST_ERROR = ErrorCode("50001", ErrorCodePrefix.SAR_WORKER)

    val S3_HEAD_OBJECT_ERROR = ErrorCode("50002", ErrorCodePrefix.SAR_WORKER)

    fun defaultErrorCodeFor(prefix: ErrorCodePrefix): ErrorCode = ErrorCode(DEFAULT_ERROR_CODE, prefix)
  }
}

enum class ErrorCodePrefix(val prefix: String) {
  SAR_WORKER("sar_worker"),

  SAR_HTML_RENDERER("sar_renderer"),

  GOTENBERG_API("gotenberg_api"),

  DOCUMENT_STORE("document_store"),

  LOCATION_API("location_api"),

  NOMIS_API("nomis_api"),

  PRISON_API("prison_api"),

  PROBATION_API("probation_api"),
}
