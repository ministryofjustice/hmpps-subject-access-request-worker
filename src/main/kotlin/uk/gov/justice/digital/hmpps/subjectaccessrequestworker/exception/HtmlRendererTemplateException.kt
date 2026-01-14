package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

class HtmlRendererTemplateException(
  attempts: Long,
  errorCode: ErrorCode,
  subjectAccessRequest: SubjectAccessRequest? = null,
  val serviceConfiguration: ServiceConfiguration,
) : SubjectAccessRequestRetryExhaustedException(
  attempts,
  null,
  ProcessingEvent.HTML_RENDERER_REQUEST,
  errorCode,
  subjectAccessRequest,
) {

  companion object {
    private const val TEMPLATE_RESOURCE_NOT_FOUND = "3000"
    private const val UNKNOWN_TEMPLATE_VERSION = "3001"
    private const val TEMPLATE_EMPTY = "3002"
    private const val TEMPLATE_NOT_FOUND = "3003"
  }

  fun getAlertServiceCacheKey(): String = "${serviceConfiguration.id}-${errorCode.getCodeStr()}"

  fun getDescription(): String = when (errorCode.code) {
    TEMPLATE_RESOURCE_NOT_FOUND -> "Service template resource not found"
    UNKNOWN_TEMPLATE_VERSION -> "Unknown template version. ${serviceConfiguration.serviceName} template hash does not " +
      "match the registered template version hash for this service."
    TEMPLATE_EMPTY -> "${serviceConfiguration.serviceName} returned an empty template"
    TEMPLATE_NOT_FOUND -> "${serviceConfiguration.serviceName} GET template request returned status 404"
    else -> "Unknown error code: ${errorCode.code}"
  }
}
