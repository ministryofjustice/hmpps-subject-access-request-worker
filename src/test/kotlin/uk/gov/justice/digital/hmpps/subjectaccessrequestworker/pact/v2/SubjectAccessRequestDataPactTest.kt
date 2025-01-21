package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact.v2

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.V4Pact
import au.com.dius.pact.core.model.annotations.Pact
import org.apache.commons.lang3.builder.EqualsBuilder
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.assertj.core.api.Assertions.assertThat
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper

class SubjectAccessRequestDataPactTest : BasePactTest() {

  companion object {
    const val CREATION_DATE_TIME_1 = "2025-01-16T12:11:04.821Z"
    val CONTENT_CREATION_DATE_TIME = DateConversionHelper().convertDates(CREATION_DATE_TIME_1)

    const val CREATION_DATE_TIME_2 = "2025-01-16T13:00:04.100Z"
    val AMENDMENT_CREATION_DATE_TIME = DateConversionHelper().convertDates(CREATION_DATE_TIME_2)

    const val CONTENT_TYPE = "Pizza"
    const val CONTENT_SUB_TYPE = "Spanner"
    const val CONTENT_TEXT_VALUE = "Once upon a midnight dreary, while I pondered, weak and weary..."
    const val CONTENT_AUTHOR_USERNAME = "Homer J Simpson"
    const val AMENDMENT_ADDITIONAL_NOTE_TEXT = "Quoth the Raven 'Nevermore'"
    const val AMENDMENT_AUTH_USERNAME = "E.A Poe"

    // The expected data entity returned by the Offender case notes SAR endpoint.
    val expectedOffenderCaseNotes = OffenderCaseNotes(
      prn = EXPECTED_PRN,
      content = listOf(
        Content(
          creationDateTime = CREATION_DATE_TIME_1,
          type = CONTENT_TYPE,
          subType = CONTENT_SUB_TYPE,
          text = CONTENT_TEXT_VALUE,
          authorUsername = CONTENT_AUTHOR_USERNAME,
          amendments = listOf(
            Amendment(
              creationDateTime = CREATION_DATE_TIME_2,
              additionalNoteText = AMENDMENT_ADDITIONAL_NOTE_TEXT,
              authorUsername = AMENDMENT_AUTH_USERNAME,
            ),
          ),
        ),
      ),
    )
  }

  @Pact(provider = "sar_offender_case_notes_provider", consumer = "sar_offender_case_notes_consumer")
  fun offenderCaseNotesDataExistsPact(builder: PactDslWithProvider): V4Pact {
    return createPact(
      builder = builder,
      pactScenario = "SAR offender case notes data exists",
      responseBody = PactDslJsonBody()
        .stringType("prn", expectedOffenderCaseNotes.prn)
        .eachLike(
          "content",
          PactDslJsonBody()
            .stringType("creationDateTime", CREATION_DATE_TIME_1)
            .stringType("type", CONTENT_TYPE)
            .stringType("subType", CONTENT_SUB_TYPE)
            .stringType("text", CONTENT_TEXT_VALUE)
            .stringType("authorUsername", CONTENT_AUTHOR_USERNAME)
            .eachLike(
              "amendments",
              PactDslJsonBody()
                .stringType("creationDateTime", CREATION_DATE_TIME_2)
                .stringType("additionalNoteText", AMENDMENT_ADDITIONAL_NOTE_TEXT)
                .stringType("authorUsername", AMENDMENT_AUTH_USERNAME),
            ),
        ),
    )
  }

  @Test
  @PactTestFor(pactMethod = "offenderCaseNotesDataExistsPact")
  fun verifyTemplateTest(mockServer: MockServer) {
    val sarResponseEntity: SarResponseEntity = getSubjectAccessRequestServiceData(mockServer)
    assertThat(sarResponseEntity).isNotNull

    val actualOffenderCaseNotes = sarResponseEntity.convertTo(OffenderCaseNotes::class.java)
    assertThat(actualOffenderCaseNotes).isEqualTo(expectedOffenderCaseNotes)

    val renderedHtml = templateRenderService.renderTemplate(
      serviceName = "offender-case-notes",
      serviceData = actualOffenderCaseNotes.content,
    )

    assertRenderedHtmlContent(renderedHtml)
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
        "Type", CONTENT_TYPE,
        "Sub type", CONTENT_SUB_TYPE,
        "Creation date time", CONTENT_CREATION_DATE_TIME,
        "Author name", CONTENT_AUTHOR_USERNAME,
      ),
    )
    doc.assertTableContent(
      cssQuery = ".data-table",
      elementIndexToQuery = 0,
      expectedTableContent = listOf("Text", CONTENT_TEXT_VALUE),
    )

    // Assert Amendments section
    assertThat(doc.body().select("h3")).hasSize(1)
    assertThat(doc.body().select("h3").first()?.text()).isEqualTo("Amendment")
    doc.assertTableContent(
      cssQuery = ".summary-list",
      elementIndexToQuery = 1,
      expectedTableContent = listOf(
        "Creation date time", AMENDMENT_CREATION_DATE_TIME,
        "Author Name", AMENDMENT_AUTH_USERNAME,
      ),
    )
    doc.assertTableContent(
      cssQuery = ".data-table",
      elementIndexToQuery = 1,
      expectedTableContent = listOf("Additional note text", AMENDMENT_ADDITIONAL_NOTE_TEXT),
    )
  }

  data class OffenderCaseNotes(
    val prn: String?,
    val content: List<Content>?,
  ) {
    override fun equals(other: Any?): Boolean = EqualsBuilder.reflectionEquals(this, other)
    override fun hashCode(): Int = HashCodeBuilder.reflectionHashCode(this)
  }

  data class Content(
    val creationDateTime: String?,
    val type: String?,
    val subType: String?,
    val text: String?,
    val authorUsername: String?,
    val amendments: List<Amendment>?,
  ) {
    override fun equals(other: Any?): Boolean = EqualsBuilder.reflectionEquals(this, other)
    override fun hashCode(): Int = HashCodeBuilder.reflectionHashCode(this)
  }

  data class Amendment(
    val creationDateTime: String,
    val additionalNoteText: String,
    val authorUsername: String,
  ) {
    override fun equals(other: Any?): Boolean = EqualsBuilder.reflectionEquals(this, other)
    override fun hashCode(): Int = HashCodeBuilder.reflectionHashCode(this)
  }
}
