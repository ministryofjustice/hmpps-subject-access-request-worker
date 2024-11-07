package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.github.jknack.handlebars.Handlebars
import com.github.mustachejava.DefaultMustacheFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.StringReader
import java.io.StringWriter

@Service
class TemplateRenderService(
  private val templateHelpers: TemplateHelpers,
) {

  fun renderServiceTemplate(serviceName: String, serviceData: Any?): String? {
    val handlebars = Handlebars()
    handlebars.registerHelpers(templateHelpers)
    val serviceTemplate = getServiceTemplate(serviceName) ?: return null
    val compiledServiceTemplate = handlebars.compileInline(serviceTemplate)
    val renderedServiceTemplate = compiledServiceTemplate.apply(serviceData)
    return renderedServiceTemplate.toString()
  }

  fun renderStyleTemplate(renderedServiceTemplate: String): String {
    val defaultMustacheFactory = DefaultMustacheFactory()
    val styleTemplate = getStyleTemplate()
    val compiledStyleTemplate = defaultMustacheFactory.compile(StringReader(styleTemplate), "styleTemplate")
    val renderedStyleTemplate = StringWriter()
    compiledStyleTemplate.execute(
      renderedStyleTemplate,
      mapOf("serviceTemplate" to renderedServiceTemplate),
    ).flush()
    return renderedStyleTemplate.toString()
  }

  fun renderTemplate(serviceName: String, serviceData: Any?): String? {
    val renderedServiceTemplate = renderServiceTemplate(serviceName, serviceData) ?: return null
    val renderedStyleTemplate = renderStyleTemplate(renderedServiceTemplate)
    return renderedStyleTemplate
  }

  fun getServiceTemplate(serviceName: String): String? {
    val file = this::class.java
      .getResource("/templates/template_$serviceName.mustache")
      ?: return null
    return file.readText()
  }

  fun getStyleTemplate(): String {
    val file = this::class.java
      .getResource("/templates/main_stylesheet.mustache")
      ?.readText() ?: ""
    return file
  }
}
