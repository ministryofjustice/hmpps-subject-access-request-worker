package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v1

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class SubjectAccessRequestDataPactTest : BasePactTest() {

  companion object {
    const val CREATION_DATE_TIME = "2025-01-16T12:11:04.821Z"
    const val CREATION_DATE_TIME_2 = "2025-01-16T13:00:04.100Z"
    const val TYPE = "some type value"
    const val SUB_TYPE = "some subType value"
    const val TEXT = "some text value"
    const val AUTHOR_USERNAME = "some auth username value"
    const val ADDITIONAL_NOTE_TEXT_VALUE = "some note text value"

    val SUCCESS_BODY = PactDslJsonBody()
      .stringType("prn", PRN)
      .eachLike(
        "content",
        PactDslJsonBody()
          .stringType("creationDateTime", CREATION_DATE_TIME)
          .stringType("type", TYPE)
          .stringType("subType", SUB_TYPE)
          .stringType("text", TEXT)
          .stringType("authorUsername", AUTHOR_USERNAME)
          .eachLike(
            "amendments",
            PactDslJsonBody()
              .stringType("creationDateTime", CREATION_DATE_TIME_2)
              .stringType("additionalNoteText", ADDITIONAL_NOTE_TEXT_VALUE)
              .stringType("authorUsername", AUTHOR_USERNAME),
          ),
      )
  }

  @Pact(provider = "sar_offender_case_notes_provider", consumer = "sar_offender_case_notes_consumer")
  fun offenderCaseNotesDataExistsPact(builder: PactDslWithProvider): V4Pact {
    return createPact(
      builder = builder,
      pactScenario = "SAR offender case notes data exists",
      prn = PRN,
      fromDate = "2024-01-01",
      toDate = "2025-01-01",
      responseBody = SUCCESS_BODY
    )
  }

  @Test
  @PactTestFor(pactMethod = "offenderCaseNotesDataExistsPact")
  fun verifyTemplateTest(mockServer: MockServer) {
    whenever(userDetailsRepository.findByUsername(any()))
      .thenReturn(null)

    val sarResponseEntity: SarResponseEntity = getSubjectAccessRequestServiceData(mockServer)

    val offenderCaseNotes = assertResponseEntity(sarResponseEntity)
    println(templateRenderService.renderTemplate("offender-case-notes", offenderCaseNotes.content))
  }

  fun assertResponseEntity(sarResponseEntity: SarResponseEntity): OffenderCaseNotes {
    assertThat(sarResponseEntity).isNotNull

    val offenderCaseNotesEntity = sarResponseEntity.toModel(OffenderCaseNotes::class.java)

    assertThat(offenderCaseNotesEntity.prn).isEqualTo(PRN)

    val contents = offenderCaseNotesEntity.content
    assertThat(contents).isNotNull
    assertThat(contents).hasSize(1)

    val content = contents!![0]
    assertThat(content).isNotNull
    assertThat(content.creationDateTime).isEqualTo(CREATION_DATE_TIME)
    assertThat(content.type).isEqualTo(TYPE)
    assertThat(content.subType).isEqualTo(SUB_TYPE)
    assertThat(content.text).isEqualTo(TEXT)
    assertThat(content.authorUsername).isEqualTo(AUTHOR_USERNAME)
    assertThat(content.amendments).isNotNull

    val amendments = content.amendments
    assertThat(amendments).isNotNull
    assertThat(amendments).hasSize(1)

    val amendment = amendments!![0]
    assertThat(amendment).isNotNull
    assertThat(amendment.creationDateTime).isEqualTo(CREATION_DATE_TIME_2)
    assertThat(amendment.additionalNoteText).isEqualTo(ADDITIONAL_NOTE_TEXT_VALUE)
    assertThat(amendment.authorUsername).isEqualTo(AUTHOR_USERNAME)

    return offenderCaseNotesEntity
  }

  data class OffenderCaseNotes(
    val prn: String?,
    val content: List<Content>?,
  )

  data class Content(
    val creationDateTime: String?,
    val type: String?,
    val subType: String?,
    val text: String?,
    val authorUsername: String?,
    val amendments: List<Amendment>?,
  )

  data class Amendment(
    val creationDateTime: String,
    val additionalNoteText: String,
    val authorUsername: String,
  )
}
