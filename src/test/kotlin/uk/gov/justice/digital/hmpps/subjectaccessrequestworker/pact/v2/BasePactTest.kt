package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v2

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.core.model.V4Pact
import com.microsoft.applicationinsights.TelemetryClient
import com.nimbusds.jose.shaded.gson.GsonBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
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

typealias SarResponseEntity = Map<*, *>?

const val EXPECTED_PRN = "A1234BC"
const val FROM_DATE = "01/01/2024"
const val TO_DATE = "01/01/2025"
const val EXPECTED_FROM_DATE = "2024-01-01"
const val EXPECTED_TO_DATE = "2025-01-01"

@ExtendWith(PactConsumerTestExt::class)
abstract class BasePactTest {

  companion object {
    val GSON = GsonBuilder().setPrettyPrinting().create()
  }

  protected val webClientConfig = WebClientConfiguration(
    documentStorageApiBaseUri = "",
    prisonApiBaseUri = "",
    probationApiBaseUri = "",
    hmppsAuthBaseUri = "",
    healthTimeout = Duration.ofSeconds(1),
    timeout = Duration.ofSeconds(10),
    documentStoreTimeout = Duration.ofSeconds(0),
    maxRetries = 1,
    backOff = "PT1M",
  )

  protected val retriesSpec = WebClientRetriesSpec(
    webClientConfig,
    mock(TelemetryClient::class.java),
  )

  protected val dynamicServicesClient = DynamicServicesClient(
    WebClient.create(),
    retriesSpec,
  )

  protected val templateRenderService = TemplateRenderService(
    TemplateHelpers(
      userDetailsRepository = mock(UserDetailsRepository::class.java),
      prisonDetailsRepository = mock(PrisonDetailsRepository::class.java),
    ),
  )

  protected fun createPact(
    pactScenario: String,
    builder: PactDslWithProvider,
    responseBody: PactDslJsonBody,
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
      .body(responseBody)
      .toPact(V4Pact::class.java)
  }

  fun <T> SarResponseEntity.convertTo(t: Class<T>): T {
    val result = GSON.fromJson(GSON.toJson(this), t)
    assertThat(result).isNotNull
    return result
  }

  protected fun getSubjectAccessRequestServiceData(mockServer: MockServer): SarResponseEntity {
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
}