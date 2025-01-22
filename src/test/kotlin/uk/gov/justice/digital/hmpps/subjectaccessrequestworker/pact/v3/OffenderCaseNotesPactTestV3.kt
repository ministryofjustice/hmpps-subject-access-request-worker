package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v3

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.BasePactTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper

class OffenderCaseNotesPactTestV3 : BasePactTest() {

  private val dateConverter = DateConversionHelper()
  private var mockServerResponseJson: String =
    getResponseStubJsonAsString("/pdf/testutil/stubs/offender-case-notes-stub.json")

  // Fields to exclude for the rendered HTML assertion
  private val exclusions = setOf(
    "prn",
  )

  // Map of fields transformed during the template rendering and their expected values after transformation
  private val transformations = mapOf(
    "content[0].amendments[0].creationDateTime" to dateConverter.convertDates("2024-02-02T15:26:48.205242"),
    "content[0].creationDateTime" to dateConverter.convertDates("2024-01-30T17:29:59.142356"),
    "content[1].amendments[0].creationDateTime" to dateConverter.convertDates("2024-02-02T15:26:48.205242"),
    "content[1].creationDateTime" to dateConverter.convertDates("2024-01-30T17:29:59.142356"),
    "content[2].amendments[0].creationDateTime" to dateConverter.convertDates("2024-01-30T14:54:12.520707"),
    "content[2].amendments[1].creationDateTime" to dateConverter.convertDates("2024-01-30T14:59:46.747803"),
    "content[2].amendments[2].creationDateTime" to dateConverter.convertDates("2024-01-30T15:00:13.644075"),
    "content[2].amendments[3].creationDateTime" to dateConverter.convertDates("2024-01-30T15:01:21.907679"),
    "content[2].creationDateTime" to dateConverter.convertDates("2024-01-29T15:00:59.618572"),
  )

  @Pact(provider = "v3", consumer = "v3")
  fun offenderCaseNotesConsumerPactV3(builder: PactDslWithProvider): V4Pact {
    return createPact(
      builder = builder,
      pactScenario = "some value",
      responseBody = mockServerResponseJson,
    )
  }

  @Test
  @PactTestFor(pactMethod = "offenderCaseNotesConsumerPactV3")
  fun verifyTemplateTest(mockServer: MockServer) {
    // Call the SAR data endpoint.
    val actualResponse = getSubjectAccessRequestServiceData(mockServer)

    // The stub response json file should be equal to the actual SAR endpoint response
    val expectedResponse = objectMapper.readValue(mockServerResponseJson, Map::class.java)
    assertThat(actualResponse).isEqualTo(expectedResponse)

    // Render the template from the response data
    val renderedHtml = templateRenderService.renderTemplate(
      serviceName = "offender-case-notes",
      serviceData = actualResponse!!["content"],
    )

    // Flatten json into a list of pairs of "json_path -> value"
    // Filter out any intentionally excluded fields
    // Apply transforms if applicable - transform dates string to the right format, names to IDs etc.
    val expectedJsonValues: List<Node> = JSONObject(mockServerResponseJson)
      .flatten()
      .filter { !exclusions.contains(it.jsonPath) }
      .map { Node(it.jsonPath, transformations.getOrDefault(it.jsonPath, it.value)) }

    // Assert that the HTML contains every expected value.
    expectedJsonValues.forEach { node ->
      println("asserting ${node.jsonPath}=${node.value} exists in rendered HTML")

      assertThat(renderedHtml)
        .withFailMessage("JSON value ${node.jsonPath}=${node.value} not present in rendered html")
        .contains(node.value.toString())
    }
  }

  private fun JSONObject.flatten(): MutableSet<Node> {
    val set = mutableSetOf<Node>()
    this.keys().forEachRemaining { key -> this.flattenJsonValue(set, key, this.get(key)) }
    return set
  }

  private fun JSONObject.flattenJsonValue(set: MutableSet<Node>, path: String, item: Any) {
    when (item) {
      is JSONObject -> item.keys().forEachRemaining { key -> flattenJsonValue(set, "$path.$key", item.get(key)) }
      is JSONArray -> item.forEachIndexed { i, element -> flattenJsonValue(set, "$path[$i]", element) }
      else -> {
        if (!exclusions.contains(path)) {
          set.add(Node(path, transformations.getOrDefault(path, item)))
        }
      }
    }
  }

  private data class Node(val jsonPath: String, val value: Any?)
}