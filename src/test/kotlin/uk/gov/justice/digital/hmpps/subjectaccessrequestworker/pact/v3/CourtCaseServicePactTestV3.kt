package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v3

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v3.Transformation.Companion.dateTransformer

class CourtCaseServicePactTestV3 : AbstractPactTest() {

  @Pact(provider = "court_case_service_provider_v3", consumer = "court_case_service_")
  fun courtCaseServicePactTest(builder: PactDslWithProvider): V4Pact {
    return createPact(builder = builder, pactScenario = "offender case notes has data")
  }

  @Test
  @PactTestFor(pactMethod = "courtCaseServicePactTest")
  fun verifyTemplateTest(mockServer: MockServer) {
    executePactTest(mockServer, "court-case-service")
  }

  override fun getResourceName(): String = "/pdf/testutil/stubs/court-case-service-stub-dup.json"

  override fun getExclusions(): Set<String> = emptySet()

  override fun getTransformations(): List<Transformation> = listOf(
    Transformation("^content\\.comments\\[\\d+\\]\\.lastUpdated\$", dateTransformer),
    Transformation("^content\\.comments\\[\\d+\\]\\.created\$", dateTransformer),
    Transformation("^content\\.hearingOutcomes\\[\\d+\\]\\.createdDate\$", dateTransformer),
    Transformation("^content\\.hearingOutcomes\\[\\d+\\]\\.outcomeDate\$", dateTransformer),
    Transformation("^content\\.hearingOutcomes\\[\\d+\\]\\.resultedDate\$", dateTransformer),
  )
}