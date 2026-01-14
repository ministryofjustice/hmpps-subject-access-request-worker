package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client

import com.slack.api.methods.MethodsClient
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import com.slack.api.model.block.Blocks.asBlocks
import com.slack.api.model.block.Blocks.context
import com.slack.api.model.block.Blocks.divider
import com.slack.api.model.block.Blocks.header
import com.slack.api.model.block.Blocks.section
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import com.slack.api.model.block.composition.BlockCompositions.plainText
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.HtmlRendererTemplateException

@Component
class SlackAlertService(
  @Value("\${slack.bot.dev-help-channel-id}") private val devHelpChannelId: String,
  @Value("\${slack.bot.template-error-recipients}") private val templateErrorRecipients: List<String>,
  private val slackClient: MethodsClient,
) {

  companion object {
    private val LOG = LoggerFactory.getLogger(SlackAlertService::class.java)
    private const val TEMPLATE_RESOURCE_NOT_FOUND = "3000"
    private const val UNKNOWN_TEMPLATE_VERSION = "3001"
    private const val TEMPLATE_EMPTY = "3002"
    private const val TEMPLATE_NOT_FOUND = "3003"
  }

  fun raiseHtmlRendererTemplateExceptionAlert(ex: HtmlRendererTemplateException) {
    val blocks = asBlocks(
      header { h ->
        h.text(plainText(":siren: Subject Access Request Templating Exception :siren:"))
      },
      section { s ->
        s.text(
          markdownText(
            "Template Exception thrown whilst attempting to generate report from service data:\n\n",
          ),
        )
        s.fields(
          listOf(
            markdownText("Service: *${ex.serviceConfiguration.serviceName}*"),
            markdownText("Error Code: `${ex.errorCode.code}`"),
          ),
        )
      },
      section { s ->
        s.text(markdownText("Description:\n> ${resolveErrorDescription(ex)}"))
      },
      section { s ->
        s.text(
          markdownText(
            ":warning:  All Subject Request Reports requesting data from " +
              "${ex.serviceConfiguration.serviceName} will be suspended until this issue resolved. :warning:",
          ),
        )
      },
      divider(),
      context { c ->
        c.elements(
          listOf(
            markdownText(
              "Please contact <#$devHelpChannelId> if you require guidance or assistance debugging this issue.",
            ),
          ),
        )
      },
    )

    templateErrorRecipients.forEach {
      val resp = slackClient.chatPostMessage(
        ChatPostMessageRequest.builder()
          .channel(it)
          .blocks(blocks)
          .build()
      )

      if (!resp.isOk) {
        LOG.error("error sending template error slack alert: {}", resp.error)
      }
    }
  }

  private fun resolveErrorDescription(ex: HtmlRendererTemplateException): String = when (ex.errorCode.code) {
    TEMPLATE_RESOURCE_NOT_FOUND -> "Service template resource not found"
    UNKNOWN_TEMPLATE_VERSION -> "Unknown template version. ${ex.serviceConfiguration.serviceName} template hash does not match the registered template version hash for this service."
    TEMPLATE_EMPTY -> "${ex.serviceConfiguration.serviceName} returned an empty template"
    TEMPLATE_NOT_FOUND -> "${ex.serviceConfiguration.serviceName} GET template request returned status 404"
    else -> "Unknown error code: ${ex.errorCode.code}"
  }
}
