package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v3

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper

typealias TransformDataFunc = (value: Any?) -> Any?



class Transformation(jsonPathRegex: String, val transformData: TransformDataFunc) {
  private val regex = Regex(jsonPathRegex)

  fun matches(path: String): Boolean = regex.matches(path)

  fun transform(value: Any): Any = { transformData(value)}

  companion object {
    private val dateConverter = DateConversionHelper()

    val dateTransformer: TransformDataFunc = { value ->
      value?.let {
        it.takeIf { it is String }?.let { dateConverter.convertDates(value.toString()) }
      } ?: "No Data Held"
    }

    val optionalFieldTransformer: TransformDataFunc = { value -> value?.let{value}?: "No Data Held"}
  }
}