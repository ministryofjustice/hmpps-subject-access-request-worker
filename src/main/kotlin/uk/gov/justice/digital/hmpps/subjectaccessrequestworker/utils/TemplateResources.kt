package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestTemplatingException
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

  fun getServiceTemplate(subjectAccessRequest: SubjectAccessRequest?, serviceName: String): String? {
    val template = getResource("$templatesDirectory/template_$serviceName.mustache")

    if (serviceTemplateIsMandatory(serviceName) && template == null) {
      throw SubjectAccessRequestTemplatingException(
        subjectAccessRequest = subjectAccessRequest,
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

  private fun serviceTemplateIsMandatory(serviceName: String) = mandatoryServiceTemplates.contains(serviceName)

  private fun getResource(path: String) = this::class.java.getResource(path)?.readText()
}
