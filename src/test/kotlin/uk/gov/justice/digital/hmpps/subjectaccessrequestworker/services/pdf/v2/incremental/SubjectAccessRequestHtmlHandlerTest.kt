package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.xml.sax.Attributes
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.HTML_STREAM_PARSING_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

@ExtendWith(MockitoExtension::class)
class SubjectAccessRequestHtmlHandlerTest {

  private val incrementalHtmlPdfBuilder: IncrementalHtmlPdfBuilder = mock()
  private val subjectAccessRequest: SubjectAccessRequest = mock()

  @Captor
  private lateinit var emitCaptor: ArgumentCaptor<String>

  @Nested
  inner class StartElement {

    private val handler = SubjectAccessRequestHtmlHandler(
      builder = incrementalHtmlPdfBuilder,
      subjectAccessRequest = subjectAccessRequest,
      minBufferSize = 100,
    )

    @BeforeEach
    fun beforeEach() {
      handler.reset()
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", " "])
    fun `Should throw exception if qname is null or empty`(qname: String?) {
      val actual = assertThrows<FatalSubjectAccessRequestException> {
        handler.startElement("", "", qname, null)
      }

      handler.assertIsEmpty()
      assertSubjectAccessRequestHtmlHandlerException(actual, "Invalid HTML: tag name was null or empty")
      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @Test
    fun `should track the start of style tag`() {
      assertThat(handler.isInsideStyleTag()).isFalse
      handler.startElement("", "", "style", null)
      assertThat(handler.isInsideStyleTag()).isTrue
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "area",
        "base",
        "br",
        "col",
        "embed",
        "hr",
        "img",
        "input",
        "link",
        "meta",
        "source",
        "track",
        "wbr",
      ],
    )
    fun `should append void tag to buffer with self closing tag format`(voidTag: String) {
      handler.startElement("", "", voidTag, null)

      assertThat(handler.currentBuffer()).isEqualTo("<$voidTag/>")
      assertThat(handler.currentOpenTags()).isEmpty()
    }

    @Test
    fun `should return expected table depth for single table`() {
      handler.startElement("", "", "table", null)

      assertThat(handler.currentBuffer()).isEqualTo("<table>")
      assertThat(handler.currentOpenTags()).containsExactlyElementsOf(
        listOf(
          SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
        ),
      )
      assertThat(handler.currentTableDepth()).isEqualTo(1)
    }

    @Test
    fun `should track nested table depth`() {
      handler.startElement("", "", "table", null)
      handler.startElement("", "", "tr", null)
      handler.startElement("", "", "td", null)
      handler.startElement("", "", "table", null)

      assertThat(handler.currentBuffer()).isEqualTo("<table><tr><td><table>")
      assertThat(handler.currentOpenTags()).containsExactlyElementsOf(
        listOf(
          SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
          SubjectAccessRequestHtmlHandler.HtmlTag("tr", emptyMap()),
          SubjectAccessRequestHtmlHandler.HtmlTag("td", emptyMap()),
          SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
        ),
      )
      assertThat(handler.currentTableDepth()).isEqualTo(2)
    }

    @Test
    fun `should throw exception when buffer size exceeds MAX_BUFFER_SIZE`() {
      handler.addCharacters(generateLargeContent(targetSize = MAX_BUFFER_SIZE - 1))

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        handler.startElement("", "", "tr", null)
      }

      assertSubjectAccessRequestHtmlHandlerException(
        actual = actual,
        expectedMessage = "buffer size exceeded max limit exiting to prevent potential memory error",
      )
    }

    @ParameterizedTest
    @MethodSource("uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental.SubjectAccessRequestHtmlHandlerTest#tagAttributeTestCases")
    fun `should correctly capture open tags with attributes`(testCase: AttributesTestCase) {
      handler.startElement("", "", testCase.qName, testCase.mockAttributes())

      assertThat(handler.currentBuffer()).isEqualTo(testCase.expectedBufferValue)
      assertThat(handler.currentOpenTags()).containsExactlyInAnyOrderElementsOf(testCase.getExpectedHtmlTags())
      assertThat(handler.currentStyleBuffer()).isEmpty()
      assertThat(handler.currentTableDepth()).isEqualTo(testCase.expectedTableDepth)
    }
  }

  @Nested
  inner class Characters {

    private val handler = SubjectAccessRequestHtmlHandler(
      builder = incrementalHtmlPdfBuilder,
      subjectAccessRequest = subjectAccessRequest,
      minBufferSize = 100,
    )

    @BeforeEach
    fun beforeEach() {
      handler.reset()
    }

    @Test
    fun `should throw exception when buffer size exceeds MAX_BUFFER_SIZE`() {
      handler.addCharacters(generateLargeContent(targetSize = MAX_BUFFER_SIZE - 1))
      val input = "Hello world!".toCharArray()

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        handler.characters(input, 0, input.size)
      }

      assertThat(handler.currentBuffer().length).isGreaterThan(MAX_BUFFER_SIZE)
      assertThat(handler.currentTableDepth()).isZero
      assertThat(handler.currentOpenTags()).isEmpty()

      assertSubjectAccessRequestHtmlHandlerException(
        actual = actual,
        expectedMessage = "buffer size exceeded max limit exiting to prevent potential memory error",
      )
    }

    @Test
    fun `should throw exception when style buffer size exceeds MAX_STYLE_SIZE`() {
      // Fill the style buffer
      val content = generateLargeContent(targetSize = MAX_STYLE_SIZE - 1)

      handler
        .openTags(Tag("style"))
        .addCharacters(content)

      val input = "some style content here".toCharArray()

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        handler.characters(input, 0, input.size)
      }

      assertThat(handler.currentBuffer().length).isZero
      assertThat(handler.currentTableDepth()).isZero
      assertThat(handler.currentOpenTags()).isEmpty()
      assertThat(handler.currentStyleBuffer().length).isGreaterThan(MAX_STYLE_SIZE)
      assertSubjectAccessRequestHtmlHandlerException(
        actual = actual,
        expectedMessage = "style buffer size exceeded max limit exiting to prevent potential memory error",
      )
    }

    @Test
    fun `should append style content to style buffer`() {
      handler.assertIsEmpty()

      // prime the handler with an opening style tag
      handler.openTags(Tag("style"))

      val styleChars = "This is the style data".toCharArray()
      handler.characters(styleChars, 0, styleChars.size)

      assertThat(handler.currentBuffer()).isEmpty()
      assertThat(handler.currentOpenTags()).isEmpty()
      assertThat(handler.currentTableDepth()).isZero()
      assertThat(handler.currentStyleBuffer()).isEqualTo("This is the style data")
    }

    @Test
    fun `should populate buffer for non style content`() {
      handler.openTags(Tag("p"))

      val styleChars = "Once upon a midnight dreary".toCharArray()
      handler.characters(styleChars, 0, styleChars.size)

      assertThat(handler.currentStyleBuffer()).isEmpty()
      assertThat(handler.currentOpenTags()).containsAnyElementsOf(
        listOf(
          SubjectAccessRequestHtmlHandler.HtmlTag(
            "p",
            emptyMap(),
          ),
        ),
      )
      assertThat(handler.currentTableDepth()).isZero()
      assertThat(handler.currentStyleBuffer()).isEmpty()
    }
  }

  @Nested
  inner class EndElement {

    private val handler = SubjectAccessRequestHtmlHandler(
      builder = incrementalHtmlPdfBuilder,
      subjectAccessRequest = subjectAccessRequest,
      minBufferSize = 100,
    )

    @BeforeEach
    fun beforeEach() {
      handler.reset()
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", " "])
    fun `should throw exception is qName is null or empty`(qName: String?) {
      val actual = assertThrows<FatalSubjectAccessRequestException> {
        handler.endElement("", "", qName)
      }

      assertSubjectAccessRequestHtmlHandlerException(actual, "Invalid HTML: tag name was null or empty")
      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @Test
    fun `should track the end of style tag`() {
      handler.openTags(Tag("style"))
      assertThat(handler.isInsideStyleTag()).isTrue()

      handler.endElement("", "", "style")

      assertThat(handler.isInsideStyleTag()).isFalse()
      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @ParameterizedTest
    @ValueSource(
      strings = [
        "area",
        "base",
        "br",
        "col",
        "embed",
        "hr",
        "img",
        "input",
        "link",
        "meta",
        "source",
        "track",
        "wbr",
      ],
    )
    fun `should do nothing for a void tag`(qName: String) {
      handler.endElement("", "", qName)

      handler.assertIsEmpty()
      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @Test
    fun `should throw exception if openTags is empty`() {
      val actual = assertThrows<FatalSubjectAccessRequestException> {
        handler.endElement("", "", "p")
      }

      assertSubjectAccessRequestHtmlHandlerException(actual, "Invalid HTML - no closing tag on stack for <p>")
      handler.assertIsEmpty()
      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @Test
    fun `should throw exception is last open tag does not match current end tag`() {
      // populate the tag tracking with a p tag.
      handler.openTags(Tag("p"))

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        // Call end element with a different tag
        handler.endElement("", "", "tr")
      }

      assertSubjectAccessRequestHtmlHandlerException(actual, "Unexpected end tag: expected=p, actual=tr")
      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentBuffer()).isEqualTo("<p>")
      assertThat(handler.currentOpenTags()).isEmpty()
      assertThat(handler.currentTableDepth()).isZero()
      assertThat(handler.currentStyleBuffer()).isEmpty()
      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @Test
    fun `should throw exception when buffer size exceeds MAX_BUFFER_SIZE`() {
      // Prime the buffer with an open tag and content
      handler.openTags(Tag("div"))

      // Fill the buffer so its 1 character below capacity
      val targetBufferSize = MAX_BUFFER_SIZE - (handler.currentBuffer().length + 1)
      handler.addCharacters(generateLargeContent(targetBufferSize))

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        handler.endElement("", "", "div")
      }

      assertSubjectAccessRequestHtmlHandlerException(
        actual = actual,
        expectedMessage = "buffer size exceeded max limit exiting to prevent potential memory error",
      )
    }

    @Test
    fun `should decrement table depth when end tag is table`() {
      val content = "This is a table"
      handler.openTags(Tag("table")).addCharacters(content.toCharArray())
      assertThat(handler.currentTableDepth()).isOne()

      handler.endElement("", null, "table")

      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentBuffer()).isEqualTo("<table>$content</table>")
      assertThat(handler.currentOpenTags()).isEmpty()
      assertThat(handler.currentTableDepth()).isZero()
      assertThat(handler.currentStyleBuffer()).isEmpty()
    }

    @Test
    fun `should decrement table depth correctly when there is nested tabled`() {
      val content = "This is a table"

      handler
        .openTags(Tag("table"), Tag("tr"), Tag("td"), Tag("table"))
        .addCharacters(content.toCharArray())

      assertThat(handler.currentTableDepth()).isEqualTo(2)

      handler.endElement("", null, "table")

      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentBuffer()).isEqualTo("<table><tr><td><table>$content</table>")
      assertThat(handler.currentOpenTags()).containsExactly(
        SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("tr", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("td", emptyMap()),
      )
      assertThat(handler.currentTableDepth()).isEqualTo(1)
      assertThat(handler.currentStyleBuffer()).isEmpty()
    }

    @Test
    fun `should not decrement table depth when inside a nested table but end element is called with a non table tag`() {
      val content = "This is a table"

      handler.openTags(
        Tag("table"),
        Tag("tr"),
        Tag("td"),
        Tag("table"),
        Tag("tr"),
        Tag("td"),
      ).addCharacters(content.toCharArray())

      assertThat(handler.currentTableDepth()).isEqualTo(2)

      handler.endElement("", null, "td")

      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentBuffer()).isEqualTo("<table><tr><td><table><tr><td>$content</td>")
      assertThat(handler.currentOpenTags()).containsExactly(
        SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("tr", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("td", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("tr", emptyMap()),
      )
      assertThat(handler.currentTableDepth()).isEqualTo(2)
      assertThat(handler.currentStyleBuffer()).isEmpty()
    }
  }

  @Nested
  inner class Emit {
    private val minBufferSize = 100

    private val handler = SubjectAccessRequestHtmlHandler(
      builder = incrementalHtmlPdfBuilder,
      subjectAccessRequest = subjectAccessRequest,
      minBufferSize = minBufferSize,
    )

    @BeforeEach
    fun beforeEach() {
      handler.reset()
    }

    @Test
    fun `should not emit when buffer size is less than minBufferSize`() {
      handler
        .openTags(Tag("div"))
        .addCharacters("Hello World!".toCharArray())
        .closeTags(Tag("div"))

      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentBuffer()).isEqualTo("<div>Hello World!</div>")
      assertThat(handler.currentOpenTags()).isEmpty()
      assertThat(handler.currentTableDepth()).isZero()
      assertThat(handler.currentStyleBuffer()).isEmpty()

      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @Test
    fun `should not emit when buffer size == minBufferSize`() {
      // Generate content 1 character less than buffer max
      val targetLength = minBufferSize - ("<td></td>".length + 1)
      val content = generateLargeContent(targetLength)

      handler
        .openTags(Tag("td"))
        .addCharacters(content)
        .closeTags(Tag("td"))

      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentBuffer()).isEqualTo("<td>${String(content)}</td>")
      assertThat(handler.currentOpenTags()).isEmpty()
      assertThat(handler.currentTableDepth()).isZero()
      assertThat(handler.currentStyleBuffer()).isEmpty()

      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @Test
    fun `should not emit when buffer size greater than minBufferSize and tableDepth greater than 0`() {
      val content = generateLargeContent(minBufferSize + 1)

      handler
        .openTags(Tag("table"), Tag("tr"), Tag("td"))
        .addCharacters(content)
        .closeTags(Tag("td"))

      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentBuffer()).isEqualTo("<table><tr><td>${String(content)}</td>")
      assertThat(handler.currentOpenTags()).containsExactly(
        SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("tr", emptyMap()),
      )
      assertThat(handler.currentTableDepth()).isEqualTo(1)
      assertThat(handler.currentStyleBuffer()).isEmpty()

      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @Test
    fun `should emit to buffer when size greater than minBufferSize and tableDepth is 0`() {
      val content = generateLargeContent(minBufferSize + 1)
      val expectedHtml = "<style></style><div>${String(content)}</div>"

      handler
        .openTags(Tag("div"))
        .addCharacters(content)
        .closeTags(Tag("div"))

      handler.assertIsEmpty()

      verify(incrementalHtmlPdfBuilder, times(1)).append(capture(emitCaptor))
      assertThat(emitCaptor.allValues).hasSize(1)
      assertThat(emitCaptor.allValues.first()).isEqualTo(expectedHtml)
    }

    @Test
    fun `should close all tags on emit`() {
      val contentLength = minBufferSize - ("<div><table><tr><td></td></tr></table>".length)
      val content = generateLargeContent(contentLength)

      handler
        .openTags(Tag("div"), Tag("table"), Tag("tr"), Tag("td"))
        .addCharacters(content)
        .closeTags(Tag("td"), Tag("tr"), Tag("table"))

      verify(incrementalHtmlPdfBuilder, times(1)).append(capture(emitCaptor))
      assertThat(emitCaptor.allValues).hasSize(1)
      assertThat(emitCaptor.allValues.first())
        .isEqualTo("<style></style><div><table><tr><td>${String(content)}</td></tr></table></div>")
    }

    @Test
    fun `should reopen all tags not closed by end element after emit`() {
      val contentLength = minBufferSize - ("<div><table><tr><td></td></tr></table>".length)
      val content = generateLargeContent(contentLength)

      handler
        .openTags(Tag("div"), Tag("table"), Tag("tr"), Tag("td"))
        .addCharacters(content)
        .closeTags(Tag("td"), Tag("tr"), Tag("table"))

      verify(incrementalHtmlPdfBuilder, times(1)).append(capture(emitCaptor))

      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentTableDepth()).isZero
      assertThat(handler.currentBuffer()).isEqualTo("<div>")
      assertThat(handler.currentOpenTags()).containsExactly(
        SubjectAccessRequestHtmlHandler.HtmlTag("div", emptyMap()),
      )
    }

    @Test
    fun `should prefix emit with style content`() {
      val content = generateLargeContent(minBufferSize)
      handler
        .openTags(Tag("style"))
        .addCharacters("table { color:red; }".toCharArray())
        .closeTags(Tag("style"))
        .openTags(
          Tag("table"),
          Tag("tr"),
          Tag("td"),
        )
        .addCharacters(content)
        .closeTags(
          Tag("td"),
          Tag("tr"),
          Tag("table"),
        )

      verify(incrementalHtmlPdfBuilder, times(1)).append(capture(emitCaptor))
      assertThat(emitCaptor.allValues).hasSize(1)
      assertThat(emitCaptor.allValues.first())
        .isEqualTo("<style>table { color:red; }</style><table><tr><td>${String(content)}</td></tr></table>")
    }

    @Test
    fun `should not emit after table end tag if table depth is greater than 0`() {
      val content = generateLargeContent(minBufferSize)
      handler
        .openTags(Tag("style"))
        .addCharacters("table { color:red; }".toCharArray())
        .closeTags(Tag("style"))
        .openTags(
          Tag("table"),
          Tag("tr"),
          Tag("td"),
          Tag("table"),
          Tag("tr"),
          Tag("td"),
        )
        .addCharacters(content)
        .closeTags(
          Tag("td"),
          Tag("tr"),
          Tag("table"),
        )

      verifyNoInteractions(incrementalHtmlPdfBuilder)

      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentTableDepth()).isEqualTo(1)
      assertThat(handler.currentBuffer()).isEqualTo("<table><tr><td><table><tr><td>${String(content)}</td></tr></table>")
      assertThat(handler.currentOpenTags()).containsExactly(
        SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("tr", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("td", emptyMap()),
      )
    }

    @Test
    fun `should emit after table end tag if table depth is equal to 0`() {
      val content = generateLargeContent(minBufferSize)

      handler
        .openTags(Tag("style"))
        .addCharacters("table { color:red; }".toCharArray())
        .closeTags(Tag("style"))
        .openTags(
          Tag("table"),
          Tag("tr"),
          Tag("td"),
          Tag("table"),
          Tag("tr"),
          Tag("td"),
        )
        .addCharacters(content)
        .closeTags(
          Tag("td"),
          Tag("tr"),
          Tag("table"),
          Tag("td"),
          Tag("tr"),
          Tag("table"),
        )

      verify(incrementalHtmlPdfBuilder).append(capture(emitCaptor))
      assertThat(emitCaptor.allValues).hasSize(1)
      assertThat(emitCaptor.allValues.first())
        .isEqualTo("<style>table { color:red; }</style><table><tr><td><table><tr><td>${String(content)}</td></tr></table></td></tr></table>")

      assertThat(handler.isInsideStyleTag()).isFalse()
      assertThat(handler.currentTableDepth()).isEqualTo(0)
      assertThat(handler.currentBuffer()).isEmpty()
      assertThat(handler.currentOpenTags()).isEmpty()
      assertThat(handler.currentStyleBuffer()).isEqualTo("table { color:red; }")
    }
  }

  internal fun SubjectAccessRequestHtmlHandler.assertIsEmpty() {
    assertThat(this.isInsideStyleTag()).isFalse()
    assertThat(this.currentBuffer()).isEmpty()
    assertThat(this.currentOpenTags()).isEmpty()
    assertThat(this.currentTableDepth()).isEqualTo(0)
    assertThat(this.currentStyleBuffer()).isEmpty()
  }

  private fun generateLargeContent(targetSize: Int): CharArray {
    val lotsOfContent = StringBuilder()
    do {
      lotsOfContent.append("a")
    } while (lotsOfContent.length < targetSize)
    return lotsOfContent.toString().toCharArray()
  }

  private fun assertSubjectAccessRequestHtmlHandlerException(
    actual: FatalSubjectAccessRequestException,
    expectedMessage: String,
  ) {
    assertThat(actual.message).contains(expectedMessage)
    assertThat(actual.cause).isNull()
    assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)
    assertThat(actual.event).isEqualTo(ProcessingEvent.HTML_STREAMING_HANDLER)
    assertThat(actual.errorCode).isEqualTo(HTML_STREAM_PARSING_ERROR)
  }

  internal fun SubjectAccessRequestHtmlHandler.openTags(vararg tags: Tag): SubjectAccessRequestHtmlHandler {
    tags.forEach { tag -> this.startElement("", "", tag.qName, tag.attributes) }
    return this
  }

  internal fun SubjectAccessRequestHtmlHandler.addCharacters(content: CharArray): SubjectAccessRequestHtmlHandler {
    this.characters(content, 0, content.size)
    return this
  }

  internal fun SubjectAccessRequestHtmlHandler.closeTags(vararg tags: Tag): SubjectAccessRequestHtmlHandler {
    tags.forEach { tag -> this.endElement("", "", tag.qName) }
    return this
  }

  companion object {

    data class Tag(val qName: String, val attributes: Attributes? = null)

    data class AttributesTestCase(
      val qName: String,
      val attributesMap: Map<String, String>,
      val expectedBufferValue: String,
      val expectedTableDepth: Int,
    ) {

      fun mockAttributes(): Attributes {
        val attributesMock: Attributes = mock()
        whenever(attributesMock.length).thenReturn(this.attributesMap.keys.size)

        this.attributesMap.entries.forEachIndexed { index, entry ->
          whenever(attributesMock.getQName(index)).thenReturn(entry.key)
          whenever(attributesMock.getValue(index)).thenReturn(entry.value)
        }
        return attributesMock
      }

      fun getExpectedHtmlTags(): List<SubjectAccessRequestHtmlHandler.HtmlTag> = listOf(
        SubjectAccessRequestHtmlHandler.HtmlTag(
          qName = qName,
          attributes = attributesMap,
        ),
      )
    }

    @JvmStatic
    fun tagAttributeTestCases(): List<AttributesTestCase> = listOf(
      AttributesTestCase(
        qName = "div",
        attributesMap = emptyMap(),
        expectedBufferValue = "<div>",
        expectedTableDepth = 0,
      ),
      AttributesTestCase(
        qName = "table",
        attributesMap = mapOf("id" to "table-1"),
        expectedBufferValue = """<table id="table-1">""",
        expectedTableDepth = 1,
      ),
      AttributesTestCase(
        qName = "div",
        attributesMap = mapOf(
          "id" to "div-1",
          "class" to "div-1-class",
        ),
        expectedBufferValue = """<div id="div-1" class="div-1-class">""",
        expectedTableDepth = 0,
      ),
      AttributesTestCase(
        qName = "tr",
        attributesMap = mapOf(
          "id" to "tr-1",
          "class" to "tr-1-class",
          "data-service" to "nomis",
        ),
        expectedBufferValue = """<tr id="tr-1" class="tr-1-class" data-service="nomis">""",
        expectedTableDepth = 0,
      ),
    )
  }
}
