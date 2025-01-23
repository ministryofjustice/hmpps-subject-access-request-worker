package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v3

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.core.model.V4Pact
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.or
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.WebClientConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.TemplateRenderService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.WebClientRetriesSpec
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

const val EXPECTED_PRN = "A1234BC"
const val FROM_DATE = "01/01/2024"
const val TO_DATE = "01/01/2025"
const val EXPECTED_FROM_DATE = "2024-01-01"
const val EXPECTED_TO_DATE = "2025-01-01"

@ExtendWith(PactConsumerTestExt::class)
abstract class AbstractPactTest {

  private val objectMapper = ObjectMapper()

  private val webClientConfig = WebClientConfiguration(
    documentStorageApiBaseUri = "",
    prisonApiBaseUri = "",
    probationApiBaseUri = "",
    hmppsAuthBaseUri = "",
    healthTimeout = Duration.ofSeconds(1),
    timeout = Duration.ofSeconds(1),
    documentStoreTimeout = Duration.ofSeconds(1),
    maxRetries = 1,
    backOff = "PT1M",
  )

  private val retriesSpec = WebClientRetriesSpec(
    webClientConfig,
    mock(TelemetryClient::class.java),
  )

  private val dynamicServicesClient = DynamicServicesClient(
    WebClient.create(),
    retriesSpec,
  )

  private val templateRenderService = TemplateRenderService(
    TemplateHelpers(
      userDetailsRepository = mock(UserDetailsRepository::class.java),
      prisonDetailsRepository = mock(PrisonDetailsRepository::class.java),
    ),
  )

  protected fun createPact(
    pactScenario: String,
    builder: PactDslWithProvider,
  ): V4Pact {
    return builder
      .given(pactScenario)
      .uponReceiving("a subject access request")
      .path("/subject-access-request")
      .matchQuery("prn", EXPECTED_PRN)
      .queryMatchingDate("fromDate", EXPECTED_FROM_DATE)
      .queryMatchingDate("toDate", EXPECTED_TO_DATE)
      .method("GET")
      .willRespondWith()
      .status(200)
      .headers(mapOf("Content-Type" to "application/json"))
      .body(getResponseStubJsonAsString(getResourceName()))
      .toPact(V4Pact::class.java)
  }

  /**
   * Execute the Pact test.
   */
  fun executePactTest(mockServer: MockServer, templateName: String) {
    val expectedResponseJson = getResponseStubJsonAsString(getResourceName())

    // Call the SAR data endpoint.
    val actualResponse = getSubjectAccessRequestServiceData(mockServer)

    // The stub response json file should be equal to the actual SAR endpoint response
    val expectedResponseObj = objectMapper.readValue(expectedResponseJson, Map::class.java)
    assertThat(actualResponse).isEqualTo(expectedResponseObj)

    // Render the template from the response data
    val renderedHtml = templateRenderService.renderTemplate(
      serviceName = templateName,
      serviceData = actualResponse!!["content"],
    )

    // Flatten json into a list of pairs of "json_path -> value"
    // Filter out any fields that are intentionally excluded from the HTML.
    // Apply data transformations to required fields - converts dates string to the expected report format, names to IDs etc.
    val expectedJsonValues: List<SarDataElement> = JSONObject(expectedResponseJson)
      .flatten()
      .filter { !getExclusions().contains(it.jsonPath) }
      .map {
        val transformData = getTransformations()?.findByPathMatch(it.jsonPath)
        SarDataElement(jsonPath = it.jsonPath, value = transformData?.invoke(it.value)?: it.value)
      }

    // Assert that the generated HTML contains every expected value.
    expectedJsonValues.forEach { element ->
      assertThat(renderedHtml)
        .withFailMessage("""
          JSON value ${element.jsonPath} not present in rendered HTML, expected '${element.value}'
          Action: Check if this field been omitted intentionally, or if a data transformation is being applied.
          
        """.trimMargin())
        .contains(element.value.toString())
    }
  }

  private fun getSubjectAccessRequestServiceData(mockServer: MockServer): Map<*, *>? {
    hmppsAuth.stubGrantToken()

    val resp = dynamicServicesClient.getDataFromService(
      serviceUrl = mockServer.getUrl(),
      prn = EXPECTED_PRN,
      crn = null,
      dateFrom = LocalDate.parse(FROM_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy")),
      dateTo = LocalDate.parse(TO_DATE, DateTimeFormatter.ofPattern("dd/MM/yyyy")),
      SubjectAccessRequest(id = UUID.randomUUID()),
    )
    return resp?.body
  }

  private fun getResponseStubJsonAsString(resourceName: String): String {
    val jsonString = this::class.java
      .getResourceAsStream(resourceName)?.use { input ->
        input.bufferedReader().use { reader ->
          objectMapper.readValue(reader, JsonNode::class.java).toString()
        }
      }

    assertThat(jsonString).isNotNull()
    assertThat(jsonString).isNotEmpty()

    return jsonString!!
  }

  private fun JSONObject.flatten(): MutableSet<SarDataElement> {
    val set = mutableSetOf<SarDataElement>()
    this.keys().forEachRemaining { key -> this.flattenJsonValue(set, key, this.get(key)) }
    return set
  }

  private fun JSONObject.flattenJsonValue(set: MutableSet<SarDataElement>, path: String, item: Any) {
    when (item) {
      is JSONObject -> item.keys().forEachRemaining { key -> flattenJsonValue(set, "$path.$key", item.get(key)) }
      is JSONArray -> item.forEachIndexed { i, element -> flattenJsonValue(set, "$path[$i]", element) }
      else -> set.add(SarDataElement(path, item))
    }
  }

  /**
   * Get the name of the JSON stub resource to load.
   */
  abstract fun getResourceName(): String

  /**
   * Get the list of json fields to exclude from the test.
   */
  abstract fun getExclusions(): Set<String>

  /**
   * Map of fields transformed during the template rendering and their expected values after transformation
   */
  abstract fun getTransformations(): List<Transformation>?

  private data class SarDataElement(val jsonPath: String, val value: Any)

  private fun List<Transformation>.findByPathMatch(path: String): TransformDataFunc? {
    return this.firstOrNull { t -> t.matches(path) }?.transformData
  }
}
