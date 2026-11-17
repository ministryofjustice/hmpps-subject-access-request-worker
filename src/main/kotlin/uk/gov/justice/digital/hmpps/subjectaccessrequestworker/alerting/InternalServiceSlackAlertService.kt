package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.alerting

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequest.alerting.InternalServiceSlackAlertSender
import java.util.UUID

@Service
class InternalServiceSlackAlertService(
  @Value("\${slack.api-url}") apiUrl: String,
  @Value("\${slack.bot.auth}") token: String,
  @Value("\${slack.bot.dev-help-channel-id}") channelIds: List<String>,
  @Value("\${spring.application.name}") private val applicationName: String,
  @Value("\${slack.environment}") private val environment: String,
) {
  private val sender = InternalServiceSlackAlertSender(apiUrl, token, channelIds, applicationName, environment)

  fun sendServiceCallFailedAlert(destination: String, operation: String, requestId: UUID?, serviceName: String?) {
    sender.sendServiceCallFailedAlert(destination, operation, requestId, serviceName)
  }
}
