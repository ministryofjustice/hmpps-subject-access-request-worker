package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestTemplatingException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.TEMPLATE_NOT_FOUND
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

@Service
class TemplateResources(
  @Value("\${template-resources.directory}") private val templatesDirectory: String = "/templates",
  @Value("\${template-resources.mandatory}") private val mandatoryServiceTemplates: List<String> = listOf(
    "G1",
    "G2",
    "G3",
  ),
) {

  companion object {
    private val LOG = LoggerFactory.getLogger(TemplateResources::class.java)
  }

  fun getServiceTemplate(subjectAccessRequest: SubjectAccessRequest?, serviceName: String): String? {
    val template = getResource("$templatesDirectory/template_$serviceName.mustache")

    if (serviceTemplateIsMandatory(serviceName) && template == null) {
      throw SubjectAccessRequestTemplatingException(
        subjectAccessRequest = subjectAccessRequest,
        errorCode = TEMPLATE_NOT_FOUND,
        message = "mandatory service template does not exist",
        params = mapOf(
          "service" to serviceName,
          "requiredTemplate" to "$templatesDirectory/template_$serviceName.mustache",
        ),
      )
    }
    return template
  }

  fun getStyleTemplate(): String = getResource("$templatesDirectory/main_stylesheet.mustache") ?: ""

  private fun serviceTemplateIsMandatory(serviceName: String) = mandatoryServiceTemplates.contains(serviceName).also {
    LOG.info("is mandatory service template? $it, config: ${mandatoryServiceTemplates.joinToString(",")}")
  }

  private fun getResource(path: String) = this::class.java.getResource(path)?.readText()
}
