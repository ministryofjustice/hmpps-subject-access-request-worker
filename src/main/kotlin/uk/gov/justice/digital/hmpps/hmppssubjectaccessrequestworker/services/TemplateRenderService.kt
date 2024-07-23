package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.github.mustachejava.DefaultMustacheFactory
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import org.springframework.stereotype.Service
import java.io.StringReader
import java.io.StringWriter

@Service
class TemplateRenderService {
  fun getServiceTemplate(serviceName: String): String? {
    val file = this::class.java
      .getResource("/templates/template_$serviceName.mustache")
      ?: return null
    return file.readText()
  }

  @Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
  fun getStyleTemplate(): String {
    val file = this::class.java
      .getResource("/templates/main_stylesheet.mustache")
      ?.readText() ?: ""
    return file
  }

  fun renderServiceTemplate(serviceName: String, serviceData: Any?): String? {
    val mf = DefaultMustacheFactory()
    val serviceTemplate = getServiceTemplate(serviceName) ?: return null
    val compiledServiceTemplate = mf.compile(StringReader(serviceTemplate), "serviceTemplate.$serviceName")
    val renderedServiceTemplate = StringWriter()
    compiledServiceTemplate.execute(renderedServiceTemplate, serviceData).flush()
    return renderedServiceTemplate.toString()
  }

  fun renderStyleTemplate(renderedServiceTemplate: String): String {
    val mf = DefaultMustacheFactory()
    val styleTemplate = getStyleTemplate()
    val compiledStyleTemplate = mf.compile(StringReader(styleTemplate), "styleTemplate")
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
}
