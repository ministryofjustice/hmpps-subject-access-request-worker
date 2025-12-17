package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting

import com.github.benmanes.caffeine.cache.Cache
import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.HtmlRendererTemplateException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException

/**
 * Class added to create abstraction between static Sentry class and services calling it. Class doesn't add or change
 * functionality it just makes it easier to mock the alerting functionality in tests.
 */
@Service
class SentryAlertsService(
  @Qualifier("alertServiceCache") val alertServiceCache: Cache<String, String>,
) : AlertsService {

  companion object {
    private val log = LoggerFactory.getLogger(SentryAlertsService::class.java)
  }

  override fun raiseReportErrorAlert(ex: SubjectAccessRequestException) {
    when (ex) {
      is HtmlRendererTemplateException -> {
        val key = ex.getAlertServiceCacheKey()
        alertServiceCache.getIfPresent(key)?.let {
          log.debug("alert cache entry already exists for key: {}, no alert will be sent", key)
        } ?: run {
          alertServiceCache.put(key, key)
          Sentry.captureException(ex)
        }
      }

      else -> Sentry.captureException(ex)
    }
  }
}
