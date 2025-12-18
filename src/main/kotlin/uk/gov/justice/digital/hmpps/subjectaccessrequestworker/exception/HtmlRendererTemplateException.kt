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
  fun getAlertServiceCacheKey(): String = "${serviceConfiguration.id}-${errorCode.getCodeStr()}"
}
