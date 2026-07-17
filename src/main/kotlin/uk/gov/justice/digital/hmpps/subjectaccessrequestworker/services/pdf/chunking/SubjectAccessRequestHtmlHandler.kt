package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking

import aws.smithy.kotlin.runtime.io.Closeable
import org.jsoup.nodes.Entities
import org.slf4j.LoggerFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

const val MAX_BUFFER_SIZE = 1_000_000
const val STYLE_TAG = "style"
const val TABLE_TAG = "table"

/**
 * Handler streams a HTML input appending the content to a buffer and periodically emits valid chunks of HTML to the
 * supplier consumer.
 *
 * Internally the handler maintains a list of open tags and how man tables are currently open. The buffer will only emit to the consumer when:
 * - The buffer size >= the minimum
 * - The current table depth is 0 to prevent HTML being emitted mid-table.
 *
 * Before emitting the handler appends any open tags to ensure the HTML fragment is properly closed. After emitting the
 * buffer is cleared and any open tags are appended to the buffer so the next chunk maintains the document context.
 *
 * The handler checks the buffer size after each event, if it's greater than the MAX_BUFFER_SIZE an error is thrown.
 */
class SubjectAccessRequestHtmlHandler(
  val consumer: HtmlConsumer,
  val minBufferSize: Int = 100_000,
) : DefaultHandler() {

  private val openTags: ArrayDeque<HtmlTag> = ArrayDeque()
  private val buffer: StringBuilder = StringBuilder()
  private var tableDepth = 0
  private val style = StringBuilder()
  private var insideStyleTag = false

  private val voidTags = setOf(
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
  )

  private companion object {
    private val log = LoggerFactory.getLogger(SubjectAccessRequestHtmlHandler::class.java)
  }

  override fun startDocument() {
    log.info("starting html to PDF chunking")
  }

  /**
   * Handle an open tag event
   */
  override fun startElement(
    uri: String?,
    localName: String?,
    qName: String?,
    attributes: Attributes?,
  ) {
    if (qName.isNullOrBlank()) {
      throw RuntimeException("Invalid HTML - tag name was null or empty")
    }

    if (qName == STYLE_TAG) {
      insideStyleTag = true
      return
    }

    val attributeMap = attributes.toMap()
    if (voidTags.contains(qName)) {
      buffer.appendVoidTag(qName, attributeMap)
      return
    }

    openTags.addLast(HtmlTag(qName, attributeMap))
    buffer.appendOpenTag(qName, attributeMap)
    checkChunkSize()

    if (qName == TABLE_TAG) {
      incrementTableDepth()
    }
  }

  /**
   * Handle a characters event (text been open/close tags).
   */
  override fun characters(ch: CharArray?, start: Int, length: Int) {
    checkChunkSize()

    if (insideStyleTag) {
      // Capture the style content in a separate buffer.
      ch?.let {
        style.append(it, start, length)
      }
      return
    }

    ch?.let {
      // Append the text to the buffer
      buffer.append(it, start, length)
      checkChunkSize()
    }
  }

  /**
   * Handle a close tag event.
   */
  override fun endElement(uri: String?, localName: String?, qName: String?) {
    if (qName == STYLE_TAG) {
      // Mark the end of the style tag.
      insideStyleTag = false
      return
    }

    if (voidTags.contains(qName)) {
      return
    }

    if (openTags.isEmpty()) {
      throw RuntimeException("Invalid HTML - no closing tag on stack for <$qName>")
    }

    val poppedValue = openTags.removeLast()
    if (poppedValue.qName != qName) {
      throw RuntimeException("Unexpected end tag: expected=$poppedValue, actual=$qName")
    }

    buffer.appendCloseTag(qName)
    checkChunkSize()

    if (qName == TABLE_TAG) {
      decrementTableDepth()
    }

    if (canEmit()) {
      log.info("emitting chunk: {}", buffer.length)
      emitChunkToConsumer()
    }
  }

  override fun endDocument() {
    if (buffer.isNotEmpty()) {
      log.info("emitting final chunk: {}", buffer.length)
      emitFinalChunkToConsumer()
    }
    log.info("end document {}, tableDepth: {}", openTags.size, tableDepth)
  }

  /**
   * Send the content of the buffer to the consumer.
   */
  private fun emitChunkToConsumer() {
    // Close any remaining open tags to ensure the HTML is properly formatted but keep the tags on the queue so the next
    // chunk is prefixed with the current document context
    for (i in openTags.size - 1 downTo 0) {
      val tag = openTags[i]
      buffer.appendCloseTag(tag.qName)
    }

    // Emit the buffer content to consumer and clear buffer.
    consumer.consume(convertBufferToHtmlString())
    buffer.clear()

    // Populate cleared buffer with any open tags to ensure the document content is retained in the next chunk.
    for (i in 0 until openTags.size) {
      val tag = openTags[i]
      buffer.appendOpenTag(tag.qName, tag.attributes)
    }
  }

  private fun emitFinalChunkToConsumer() {
    if (tableDepth != 0) {
      throw RuntimeException("invalid html: Document processing ended with tableDepth > 0: actual=$tableDepth")
    }

    // Close any remaining open tags to ensure the HTML is properly formatted
    while (openTags.isNotEmpty()) {
      val tag = openTags.removeLast()
      buffer.appendCloseTag(tag.qName)
    }

    // emit to consumer and clear buffer.
    consumer.consume(convertBufferToHtmlString())
    buffer.clear()
  }

  private fun incrementTableDepth() {
    tableDepth++
  }

  private fun decrementTableDepth() {
    tableDepth--
    if (tableDepth < 0) throw RuntimeException("Invalid HTML: Table depth is negative")
  }

  private fun checkChunkSize() {
    if (buffer.length >= MAX_BUFFER_SIZE) {
      throw RuntimeException("buffer size exceeded max limit exiting to prevent potential memory error")
    }
  }

  /**
   * Check if we can emit a chunk to the consumer. Emit only when the tableDepth tracker is at 0 and the buffer size is
   * the greater than the minimum size.
   */
  private fun canEmit(): Boolean = tableDepth == 0 && buffer.length >= minBufferSize

  /**
   * Appends an opening HTML tag with attributes to the buffer.
   */
  internal fun StringBuilder.appendOpenTag(
    qName: String?,
    attributes: Map<String, String>,
  ): StringBuilder = this.append("<$qName")
    .appendAttributes(attributes)
    .append(">")

  /**
   * Appends a closing HTML tag to the buffer.
   */
  internal fun StringBuilder.appendCloseTag(qName: String): StringBuilder = this.append("</$qName>")

  /**
   * Convert attribute values to valid HTML format and append to the buffer.
   */
  internal fun StringBuilder.appendAttributes(attributes: Map<String, String>): StringBuilder {
    attributes.forEach { (qName, value) ->
      append(" $qName=\"").append(Entities.escape(value)).append("\"")
    }
    return this
  }

  /**
   * Append a void tag (self-closing tag) to the buffer.
   */
  internal fun StringBuilder.appendVoidTag(
    qName: String,
    attributes: Map<String, String>,
  ): StringBuilder = this.append("<$qName").appendAttributes(attributes).append("/>")

  internal fun convertBufferToHtmlString(): String = buildString {
    appendOpenTag(STYLE_TAG, emptyMap())
      .append(style)
      .appendCloseTag(STYLE_TAG)
      .append(buffer)
  }

  fun Attributes?.toMap(): Map<String, String> = this?.let { attributes ->
    buildMap(attributes.length) {
      for (i in 0 until attributes.length) {
        put(attributes.getQName(i), attributes.getValue(i))
      }
    }
  } ?: emptyMap()
}

data class HtmlTag(val qName: String, val attributes: Map<String, String>)

interface HtmlConsumer : Closeable {

  fun consume(chunk: String)
}
