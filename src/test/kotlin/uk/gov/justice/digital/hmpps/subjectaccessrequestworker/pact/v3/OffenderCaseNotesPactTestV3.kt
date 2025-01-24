package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v3

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v3.Transformation.Companion.dateTransformer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper

class OffenderCaseNotesPactTestV3 : AbstractPactTest() {

  @Pact(provider = "offender_case_notes_provider_v3", consumer = "offender_case_notes_consumer_v3")
  fun offenderCaseNotesConsumerPactV3(builder: PactDslWithProvider): V4Pact {
    return createPact(builder = builder, pactScenario = "offender case notes has data")
  }

  @Test
  @PactTestFor(pactMethod = "offenderCaseNotesConsumerPactV3")
  fun verifyTemplateTest(mockServer: MockServer) {
    executePactTest(mockServer, "offender-case-notes")
  }

  override fun getResourceName(): String = "/pdf/testutil/stubs/offender-case-notes-stub.json"

  override fun getExclusions(): Set<String> = setOf(
    "prn",
  )

  override fun getTransformations(): List<Transformation> = listOf(
    Transformation("^content\\.comments\\[\\d+\\]\\.lastUpdated\$", dateTransformer)
  )
}
