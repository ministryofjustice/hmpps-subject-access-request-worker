package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.HTML_STREAM_PARSING_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

@ExtendWith(MockitoExtension::class)
class SubjectAccessRequestHtmlHandlerTest {

  private val incrementalHtmlPdfBuilder: IncrementalHtmlPdfBuilder = mock()
  private val subjectAccessRequest: SubjectAccessRequest = mock()

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

      assertThat(actual.message).contains("Invalid HTML: tag name was null or empty")
      assertThat(actual.cause).isNull()
      assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)
      assertThat(actual.event).isEqualTo(ProcessingEvent.HTML_STREAMING_HANDLER)
      assertThat(actual.errorCode).isEqualTo(HTML_STREAM_PARSING_ERROR)

      verifyNoInteractions(incrementalHtmlPdfBuilder)
    }

    @Test
    fun `should do nothing for style tag`() {
      handler.startElement("", "", "style", null)

      assertThat(handler.currentBuffer()).isEqualTo("")
      assertThat(handler.currentOpenTags()).isEmpty()
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
      assertThat(handler.currentOpenTags()).containsExactlyElementsOf(listOf(
        SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap())
      ))
      assertThat(handler.currentTableDepth()).isEqualTo(1)
    }

    @Test
    fun `should return expected table depth for multiple tables`() {
      handler.startElement("", "", "table", null)
      handler.startElement("", "", "table", null)
      handler.startElement("", "", "table", null)

      assertThat(handler.currentBuffer()).isEqualTo("<table><table><table>")
      assertThat(handler.currentOpenTags()).containsExactlyElementsOf(listOf(
        SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
        SubjectAccessRequestHtmlHandler.HtmlTag("table", emptyMap()),
      ))
      assertThat(handler.currentTableDepth()).isEqualTo(3)
    }

    @Test
    fun `should throw exception when buffer size exceeds MAX_BUFFER_SIZE`() {
      handler.populateBuffer(generateLargeContent(targetSize = MAX_BUFFER_SIZE - 1))

      val actual = assertThrows<FatalSubjectAccessRequestException> {
        handler.startElement("", "", "table", null)
      }

      assertThat(actual.message).contains("buffer size exceeded max limit exiting to prevent potential memory error")
      assertThat(actual.cause).isNull()
      assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)
      assertThat(actual.event).isEqualTo(ProcessingEvent.HTML_STREAMING_HANDLER)
      assertThat(actual.errorCode).isEqualTo(HTML_STREAM_PARSING_ERROR)
    }

    @Test
    fun `should add attributes name and value open tag tracking`() {
      val attributes: org.xml.sax.Attributes = mock()
      whenever(attributes.length).thenReturn(2)
      whenever(attributes.getQName(0)).thenReturn("class")
      whenever(attributes.getValue(0)).thenReturn("test-class-1")
      whenever(attributes.getQName(1)).thenReturn("id")
      whenever(attributes.getValue(1)).thenReturn("test-id-1")

      handler.startElement("", "", "table", attributes)

      assertThat(handler.currentBuffer()).isEqualTo("""<table class="test-class-1" id="test-id-1">""")
      assertThat(handler.currentOpenTags()).containsExactlyInAnyOrderElementsOf(
        listOf(
          SubjectAccessRequestHtmlHandler.HtmlTag(
            "table",
            mapOf(
              "class" to "test-class-1",
              "id" to "test-id-1",
            ),
          ),
        ),
      )
      assertThat(handler.currentTableDepth()).isEqualTo(1)
    }
  }

  private fun SubjectAccessRequestHtmlHandler.populateBuffer(content: CharArray) {
    this.characters(content, 0, content.size)
  }

  private fun generateLargeContent(targetSize: Int): CharArray {
    val lotsOfContent = StringBuilder()
    do {
      lotsOfContent.append("a")
    } while (lotsOfContent.length < targetSize)
    return lotsOfContent.toString().toCharArray()
  }
}