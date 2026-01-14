package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.SlackAlertService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.HtmlRendererTemplateException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCodePrefix
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository

class SlackAlertServiceIntText : BaseClientIntTest() {

  @Autowired
  private lateinit var clientV2: SlackAlertService

  @Autowired
  private lateinit var serviceConfigurationRepository: ServiceConfigurationRepository


  @Test
  fun testIt() {
    val serviceConfig = serviceConfigurationRepository.findByServiceName("keyworker-api")
    assertThat(serviceConfig).isNotNull

    try {
      clientV2.raiseHtmlRendererTemplateExceptionAlert(
        HtmlRendererTemplateException(
          attempts = 1,
          errorCode = ErrorCode(ErrorCodePrefix.SAR_HTML_RENDERER, "3001"),
          subjectAccessRequest = null,
          serviceConfiguration = serviceConfig!!,
        ),
      )

    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}