package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v2

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper

class SubjectAccessRequestDataPactTest : BasePactTest() {

  companion object {
    const val CREATION_DATE_TIME = "2025-01-16T12:11:04.821Z"
    val EXPECTED_CREATION_DATE_TIME = DateConversionHelper().convertDates(CREATION_DATE_TIME)

    const val CREATION_DATE_TIME_2 = "2025-01-16T13:00:04.100Z"
    val EXPECTED_CREATION_DATE_TIME_2 = DateConversionHelper().convertDates(CREATION_DATE_TIME_2)

    const val TYPE_VALUE = "Pizza"
    const val SUB_TYPE_VALUE = "Spanner"
    const val TEXT_VALUE = "Once upon a midnight dreary, while I pondered, weak and weary..."
    const val AUTHOR_USERNAME_VALUE = "Homer J Simpson"
    const val ADDITIONAL_NOTE_TEXT_VALUE = "Quoth the Raven 'Nevermore'"

    val SUCCESS_BODY = PactDslJsonBody()
      .stringType("prn", PRN)
      .eachLike(
        "content",
        PactDslJsonBody()
          .stringType("creationDateTime", CREATION_DATE_TIME)
          .stringType("type", TYPE_VALUE)
          .stringType("subType", SUB_TYPE_VALUE)
          .stringType("text", TEXT_VALUE)
          .stringType("authorUsername", AUTHOR_USERNAME_VALUE)
          .eachLike(
            "amendments",
            PactDslJsonBody()
              .stringType("creationDateTime", CREATION_DATE_TIME_2)
              .stringType("additionalNoteText", ADDITIONAL_NOTE_TEXT_VALUE)
              .stringType("authorUsername", AUTHOR_USERNAME_VALUE),
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
      responseBody = SUCCESS_BODY,
    )
  }

  @Test
  @PactTestFor(pactMethod = "offenderCaseNotesDataExistsPact")
  fun verifyTemplateTest(mockServer: MockServer) {
    val sarResponseEntity: SarResponseEntity = getSubjectAccessRequestServiceData(mockServer)
    val content = assertResponseEntity(sarResponseEntity).content!![0]

    val renderedHtml = templateRenderService.renderTemplate("offender-case-notes", content)
    assertRenderedHtmlContent(renderedHtml)
  }

  private fun assertResponseEntity(sarResponseEntity: SarResponseEntity): OffenderCaseNotes {
    assertThat(sarResponseEntity).isNotNull

    val offenderCaseNotesEntity = sarResponseEntity.toModel(OffenderCaseNotes::class.java)

    assertThat(offenderCaseNotesEntity.prn).isEqualTo(PRN)

    val contents = offenderCaseNotesEntity.content
    assertThat(contents).isNotNull
    assertThat(contents).hasSize(1)

    val content = contents!![0]
    assertThat(content).isNotNull
    assertThat(content.creationDateTime).isEqualTo(CREATION_DATE_TIME)
    assertThat(content.type).isEqualTo(TYPE_VALUE)
    assertThat(content.subType).isEqualTo(SUB_TYPE_VALUE)
    assertThat(content.text).isEqualTo(TEXT_VALUE)
    assertThat(content.authorUsername).isEqualTo(AUTHOR_USERNAME_VALUE)
    assertThat(content.amendments).isNotNull

    val amendments = content.amendments
    assertThat(amendments).isNotNull
    assertThat(amendments).hasSize(1)

    val amendment = amendments!![0]
    assertThat(amendment).isNotNull
    assertThat(amendment.creationDateTime).isEqualTo(CREATION_DATE_TIME_2)
    assertThat(amendment.additionalNoteText).isEqualTo(ADDITIONAL_NOTE_TEXT_VALUE)
    assertThat(amendment.authorUsername).isEqualTo(AUTHOR_USERNAME_VALUE)

    return offenderCaseNotesEntity
  }

  private fun assertRenderedHtmlContent(renderedHtml: String?) {
    assertThat(renderedHtml).isNotNull
    assertThat(renderedHtml).isNotEmpty()

    val doc = Jsoup.parse(renderedHtml!!)
    assertThat(doc.body().select("h1").first()?.text()).isEqualTo("Sensitive Case Notes")
    assertThat(doc.body().select("h2").first()?.text()).isEqualTo("Case note")
    assertThat(doc.body().select("table")).hasSize(4)

    doc.assertTableContent(
      cssQuery = ".summary-list",
      elementIndexToQuery = 0,
      expectedTableContent = listOf(
        "Type", TYPE_VALUE,
        "Sub type", SUB_TYPE_VALUE,
        "Creation date time", EXPECTED_CREATION_DATE_TIME,
        "Author name", AUTHOR_USERNAME_VALUE,
      )
    )
    doc.assertTableContent(
      cssQuery = ".data-table",
      elementIndexToQuery = 0,
      expectedTableContent = listOf("Text", TEXT_VALUE)
    )

    // Assert Amendments section
    assertThat(doc.body().select("h3")).hasSize(1)
    assertThat(doc.body().select("h3").first()?.text()).isEqualTo("Amendment")
    doc.assertTableContent(
      cssQuery = ".summary-list",
      elementIndexToQuery = 1,
      expectedTableContent = listOf(
        "Creation date time", EXPECTED_CREATION_DATE_TIME_2,
        "Author Name", AUTHOR_USERNAME_VALUE,
      )
    )
    doc.assertTableContent(
      cssQuery = ".data-table",
      elementIndexToQuery = 1,
      expectedTableContent = listOf("Additional note text", ADDITIONAL_NOTE_TEXT_VALUE)
    )
  }

  fun Document.assertTableContent(
    cssQuery: String,
    elementIndexToQuery: Int,
    expectedTableContent: List<String>
  ) {
    val tableElements = this.body().select(cssQuery)
    assertThat(tableElements).hasSizeGreaterThanOrEqualTo(elementIndexToQuery)

    val table = tableElements[elementIndexToQuery].select("tr > td")

    assertThat(table).hasSize(expectedTableContent.size)
    assertThat(table.map { it.text() }).containsExactlyElementsOf(expectedTableContent)
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
