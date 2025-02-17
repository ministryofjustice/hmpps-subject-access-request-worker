package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.github.jknack.handlebars.Handlebars
import com.github.mustachejava.DefaultMustacheFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateResources
import java.io.StringReader
import java.io.StringWriter

@Service
class TemplateRenderService(
  private val templateHelpers: TemplateHelpers,
  private val templateResources: TemplateResources,
) {

  fun renderServiceTemplate(
    subjectAccessRequest: SubjectAccessRequest?,
    serviceName: String,
    serviceData: Any?,
  ): String? {
    val handlebars = Handlebars()
    handlebars.registerHelpers(templateHelpers)
    val serviceTemplate = templateResources.getServiceTemplate(subjectAccessRequest, serviceName) ?: return null

    val compiledServiceTemplate = handlebars.compileInline(serviceTemplate)
    val renderedServiceTemplate = compiledServiceTemplate.apply(serviceData)
    return renderedServiceTemplate.toString()
  }

  fun renderStyleTemplate(renderedServiceTemplate: String): String {
    val defaultMustacheFactory = DefaultMustacheFactory()
    val styleTemplate = templateResources.getStyleTemplate()
    val compiledStyleTemplate = defaultMustacheFactory.compile(StringReader(styleTemplate), "styleTemplate")
    val renderedStyleTemplate = StringWriter()
    compiledStyleTemplate.execute(
      renderedStyleTemplate,
      mapOf("serviceTemplate" to renderedServiceTemplate),
    ).flush()
    return renderedStyleTemplate.toString()
  }

  fun renderTemplate(subjectAccessRequest: SubjectAccessRequest?, serviceName: String, serviceData: Any?): String? {
    val renderedServiceTemplate = renderServiceTemplate(subjectAccessRequest, serviceName, serviceData) ?: return null
    val renderedStyleTemplate = renderStyleTemplate(renderedServiceTemplate)
    return renderedStyleTemplate
  }
}
