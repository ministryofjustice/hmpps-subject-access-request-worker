package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.pact

import org.assertj.core.api.Assertions.assertThat
import org.jsoup.nodes.Document

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