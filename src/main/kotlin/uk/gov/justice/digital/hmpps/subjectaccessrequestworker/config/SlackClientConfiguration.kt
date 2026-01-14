package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import com.slack.api.Slack
import com.slack.api.methods.MethodsClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SlackClientConfiguration(
  @Value("\${slack.bot.auth}") private val slackBotAuthToken: String,
) {

  @Bean
  fun slack(): MethodsClient = Slack.getInstance().methods(slackBotAuthToken)
}
