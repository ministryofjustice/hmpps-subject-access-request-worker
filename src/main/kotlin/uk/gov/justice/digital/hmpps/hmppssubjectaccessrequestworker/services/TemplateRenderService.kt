package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.github.jknack.handlebars.Handlebars
import com.github.mustachejava.DefaultMustacheFactory
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils.DateConversionHelper
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
    val handlebars = Handlebars()
    handlebars.registerHelpers(TemplateHelpers())
    val serviceTemplate = getServiceTemplate(serviceName) ?: return null
    val compiledServiceTemplate = handlebars.compileInline(serviceTemplate)
    val renderedServiceTemplate = compiledServiceTemplate.apply(serviceData)
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

class TemplateHelpers {
  fun formatDate(input: String?): String {
    if (input == null || input == "") return ""
    return DateConversionHelper().convertDates(input)
  }

  fun optionalValue(input: Any?): Any {
    if (input == null || input == "") return "No Data Held"
    return input
  }
}
