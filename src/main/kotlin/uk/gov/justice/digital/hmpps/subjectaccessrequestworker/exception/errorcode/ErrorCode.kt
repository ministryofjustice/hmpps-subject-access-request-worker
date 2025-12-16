package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode

import org.springframework.http.HttpStatusCode

open class ErrorCode(
  private val errorPrefix: ErrorCodePrefix,
  val code: String,
) {

  constructor(
    status: HttpStatusCode,
    errorPrefix: ErrorCodePrefix,
  ) : this(errorPrefix, status.value().toString())

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
    const val DEFAULT_ERROR_CODE = "10000"

    const val EXTERNAL_API_AUTH_ERROR_CODE = "99999"


    val INTERNAL_SERVER_ERROR = ErrorCode(ErrorCodePrefix.SAR_WORKER, DEFAULT_ERROR_CODE)

    val CONFIGURATION_ERROR = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10001")

    val PRISON_SUBJECT_NAME_NOT_FOUND = ErrorCode(ErrorCodePrefix.PRISON_API, "10002")

    val PROBATION_SUBJECT_NAME_NOT_FOUND = ErrorCode(ErrorCodePrefix.PROBATION_API, "10003")

    val DOCUMENT_UPLOAD_VERIFICATION_ERROR = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10004")

    val DOCUMENT_STORE_CONFLICT = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10005")

    val TEMPLATE_NOT_FOUND = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10006")

    val TEMPLATE_HELPER_ERROR = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10007")

    val S3_GET_ERROR = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10008")

    val S3_LIST_ERROR = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10009")

    val S3_HEAD_OBJECT_ERROR = ErrorCode(ErrorCodePrefix.SAR_WORKER, "10010")

    /**
     * External API auth errors
     */
    val NOMIS_API_AUTH_ERROR = ErrorCode(ErrorCodePrefix.NOMIS_API, EXTERNAL_API_AUTH_ERROR_CODE)

    val DOCUMENT_STORE_AUTH_ERROR = ErrorCode(ErrorCodePrefix.DOCUMENT_STORE, EXTERNAL_API_AUTH_ERROR_CODE)

    val HTML_RENDERER_AUTH_ERROR = ErrorCode(ErrorCodePrefix.SAR_HTML_RENDERER, EXTERNAL_API_AUTH_ERROR_CODE)

    val PRISON_API_AUTH_ERROR = ErrorCode(ErrorCodePrefix.PRISON_API, EXTERNAL_API_AUTH_ERROR_CODE)

    val LOCATION_API_AUTH_ERROR = ErrorCode(ErrorCodePrefix.LOCATION_API, EXTERNAL_API_AUTH_ERROR_CODE)

    val PROBATION_API_AUTH_ERROR = ErrorCode(ErrorCodePrefix.PROBATION_API, EXTERNAL_API_AUTH_ERROR_CODE)

    fun defaultErrorCodeFor(prefix: ErrorCodePrefix): ErrorCode = ErrorCode(prefix, DEFAULT_ERROR_CODE)
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
