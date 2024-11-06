package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.itextpdf.layout.element.Text
import org.yaml.snakeyaml.LoaderOptions
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.ProcessDataHelper

// TODO - Extracted from the original PDF generator as is, need to revisit this to work out what its doing.
class YamlFormatter {

  fun renderAsBasicYaml(rawData: Any?): Text {
    val serviceData = format(rawData)
    val loaderOptions = LoaderOptions()
    loaderOptions.codePointLimit = 1024 * 1024 * 1024 // Max YAML size 1 GB - can be increased
    val yamlFactory = YAMLFactory.builder()
      .loaderOptions(loaderOptions)
      .build()
    val contentText =
      YAMLMapper(
        yamlFactory
          .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR),
      ).writeValueAsString(serviceData)
    val text = Text(contentText)
    text.setNextRenderer(CodeRenderer(text))
    return text
  }

  private fun format(input: Any?): Any? {
    if (input is Map<*, *>) {
      // If it's a map, process the key
      val returnMap = mutableMapOf<String, Any?>()
      val inputKeys = input.keys
      inputKeys.forEach { key ->
        returnMap[processKey(key.toString())] = format(input[key])
      }
      return returnMap
    }

    if (input is ArrayList<*> && input.isNotEmpty()) {
      val returnArray = arrayListOf<Any?>()
      input.forEach { value -> returnArray.add(format(value)) }
      return returnArray
    }

    return processValue(input)
  }

  private fun processKey(key: String): String {
    return ProcessDataHelper.camelToSentence(key)
  }

  private fun processValue(input: Any?): Any? {
    // Handle null values
    if (input is ArrayList<*> && input.isEmpty() || input == null || input == "null") {
      return "No data held"
    }
    // Handle dates/times
    if (input is String) {
      var processedValue = input
      processedValue = GeneratePdfService.dateConversionHelper.convertDates(processedValue)
      return processedValue
    }

    return input
  }
}