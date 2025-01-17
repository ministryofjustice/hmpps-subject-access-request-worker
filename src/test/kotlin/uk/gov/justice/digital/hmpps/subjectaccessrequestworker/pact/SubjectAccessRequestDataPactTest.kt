package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import com.nimbusds.jose.shaded.gson.GsonBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@ExtendWith(PactConsumerTestExt::class)
class SubjectAccessRequestDataPactTest {

  @Pact(provider = "sar_offender_case_notes_provider", consumer = "sar_offender_case_notes_consumer")
  fun offenderCaseNotesPact(builder: PactDslWithProvider): V4Pact {
    return builder
      .given("SAR offender case notes data exists", "", "")
      .uponReceiving("a subject access request")
      .path("/subject-access-request")
      .queryParameterFromProviderState("prn", "\${prn_query}", PRN)
      .queryParameterFromProviderState("fromDate", "\${fromDate_query}", FROM_DATE)
      .queryParameterFromProviderState("toDate", "\${toDate_query}", TO_DATE)
      .method("GET")
      .willRespondWith()
      .status(200)
      .headers(mapOf("Content-Type" to "application/json"))
      .body(
        PactDslJsonBody()
          .valueFromProviderState("prn", "\${prn_value}", PRN)
          .eachLike(
            "content",
            PactDslJsonBody()
              .stringType("creationDateTime", "2025-01-16T12:11:04.821Z")
              .stringType("type")
              .stringType("subType")
              .stringType("text")
              .stringType("authorUsername")
              .eachLike(
                "amendments",
                PactDslJsonBody()
                  .stringType("creationDateTime", "2025-01-16T12:11:04.821Z")
                  .stringType("additionalNoteText")
                  .stringType("authorUsername"),
              ),
          ),
      ).toPact(V4Pact::class.java)
  }

  @Test
  @PactTestFor(pactMethod = "offenderCaseNotesPact")
  fun runHttpTest(mockServer: MockServer) {
    val webClient: WebTestClient = WebTestClient
      .bindToServer()
      .baseUrl(mockServer.getUrl())
      .build()

    webClient
      .get()
      .uri("/subject-access-request?prn=$PRN&fromDate=$FROM_DATE&toDate=$TO_DATE")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.prn").isEqualTo(PRN)
      .jsonPath("$.content[0].creationDateTime").isEqualTo("2025-01-16T12:11:04.821Z")
      .jsonPath("$.content[0].type").isNotEmpty
      .jsonPath("$.content[0].subType").isNotEmpty
      .jsonPath("$.content[0].text").isNotEmpty
      .jsonPath("$.content[0].authorUsername").isNotEmpty
      .jsonPath("$.content[0].amendments").isNotEmpty
      .jsonPath("$.content[0].amendments[0].creationDateTime").isEqualTo("2025-01-16T12:11:04.821Z")
      .jsonPath("$.content[0].amendments[0].additionalNoteText").isNotEmpty
      .jsonPath("$.content[0].amendments[0].authorUsername").isNotEmpty
  }

  companion object {
    private const val PRN = "A1234BC"
    private const val FROM_DATE = "2024-01-01T00:00:00.000Z"
    private const val TO_DATE = "2025-01-01T00:00:00.000Z"
    private val gson = GsonBuilder().setPrettyPrinting().create()
  }
}
