package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v3

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.TemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers

typealias MutateFunction = (value: Any?) -> Any?

class VerifyHtmlTest {

  private val objectMapper = ObjectMapper()
  private val dateConverter = DateConversionHelper()

  private val templateRenderService = TemplateRenderService(
    TemplateHelpers(
      userDetailsRepository = mock(UserDetailsRepository::class.java),
      prisonDetailsRepository = mock(PrisonDetailsRepository::class.java),
    ),
  )

  /**
   * Mutation function to convert a date string into the expected format/value.
   */
  private val dateFormatMutation: MutateFunction = { value ->
    value?.let {
      if (it is String) {
        dateConverter.convertDates(value.toString())
      } else {
        "No Data Held"
      }
    }?: "No Data Held"
  }

  @Test
  fun verifyHtmlContainsExpectedJsonValues() {
    val sarData = getSarDataObject("/pdf/testutil/stubs/court-case-service-stub-dup.json")
    assertThat(sarData).isNotNull

    // Render the template using the stubbed data.
    val renderedHtml = templateRenderService.renderTemplate("court-case-service", sarData!!["content"])
    assertThat(renderedHtml).isNotEmpty()

    // Walk the SAR response object and assert each value exists in the html.
    val jsonObject = JSONObject(objectMapper.writeValueAsString(sarData))
    jsonObject.walk(html = renderedHtml!!, exclusions = emptySet(), mutations = mutations())
  }

  private fun getSarDataObject(filename: String): Map<*, *>? {
    val jsonStr = this::class.java.getResourceAsStream(filename)?.use { input ->
      input.bufferedReader().readText()
    }
    return objectMapper.readValue(jsonStr, Map::class.java)
  }

  private fun JSONObject.walk(html: String, exclusions: Set<String>, mutations: List<Mutation>) {
    this.keys().forEachRemaining { key ->
      this.handleJsonValue(html, key, this.get(key), exclusions, mutations)
    }
  }

  private fun JSONObject.handleJsonValue(
    html: String,
    jsonPath: String,
    element: Any,
    exclusions: Set<String>,
    mutations: List<Mutation>,
  ) {
    when (element) {
      is JSONObject -> element.keys().forEachRemaining { key ->
        handleJsonValue(html, "$jsonPath.$key", element.get(key), exclusions, mutations)
      }

      is JSONArray -> element.forEachIndexed { i, el ->
        handleJsonValue(html, "$jsonPath[$i]", el, exclusions, mutations)
      }

      else -> assertHtmlContainsJsonValue(html, jsonPath, element, exclusions, mutations)
    }
  }

  private fun assertHtmlContainsJsonValue(
    html: String,
    jsonPath: String,
    element: Any,
    exclusions: Set<String>,
    mutations: List<Mutation>,
  ) {
    // Skip assertion if the field has been explicitly excluded from the HTML.
    if (exclusions.contains(jsonPath)) {
      println("skipping excluded node: $jsonPath")
      return
    }

    // Apply mutation function to value if required for this node i.e. format a date string, or convert a name to an id etc.
    val targetValue = mutations.getMutationForPath(jsonPath)?.let { mutateData -> mutateData(element) } ?: element

    // Verify the value of the json node exists in the html.
    assertThat(html)
      .withFailMessage("JSON value '$jsonPath=$element'/mutated value=$targetValue is not present in rendered HTML")
      .contains(targetValue.toString())
  }

  /**
   * A list of fields which require a data mutation applied to them before checking the value exists in the html.
   * Uses a regex to match the json path to determine which fields it applies to.
   */
  private fun mutations(): List<Mutation> = listOf(
    Mutation("^content\\.comments\\[\\d+\\]\\.lastUpdated\$", dateFormatMutation),
    Mutation("^content\\.comments\\[\\d+\\]\\.created\$", dateFormatMutation),
    Mutation("^content\\.hearingOutcomes\\[\\d+\\]\\.createdDate\$", dateFormatMutation),
    Mutation("^content\\.hearingOutcomes\\[\\d+\\]\\.outcomeDate\$", dateFormatMutation),
    Mutation("^content\\.hearingOutcomes\\[\\d+\\]\\.resultedDate\$", dateFormatMutation),
  )

  class Mutation(jsonPathRegex: String, val mutateData: MutateFunction) {
    private val regex = Regex(jsonPathRegex)

    fun matches(path: String): Boolean = regex.matches(path)
    fun mutate(value: Any): Any = { this.mutateData(value) }
  }

  private fun List<Mutation>.getMutationForPath(jsonPath: String): MutateFunction? {
    return this.firstOrNull { t -> t.matches(jsonPath) }?.mutateData
  }
}