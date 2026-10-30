package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental

import com.microsoft.applicationinsights.TelemetryClient
import org.apache.tika.mime.MimeTypes
import org.jsoup.nodes.Entities
import org.slf4j.LoggerFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.HTML_STREAM_PARSING_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest

const val MAX_BUFFER_SIZE = 10_000_000
const val MAX_STYLE_SIZE = 1_000_000
const val STYLE_TAG = "style"
const val TABLE_TAG = "table"

/**
 * Streams an HTML document and periodically emits valid HTML fragments to an
 * IncrementalHtmlPdfBuilder.
 *
 * iText converts HTML into an in-memory DOM-like structure before rendering it
 * to PDF. For very large HTML documents this can consume substantial amounts of
 * memory and has previously resulted in OOM-kill failures. This handler
 * addresses that problem by building the PDF incrementally from smaller,
 * self-contained HTML fragments.
 *
 * By processing bounded HTML chunks, only a portion of the document needs to be
 * converted by iText at any one time. Once a chunk has been written to the PDF,
 * the associated objects can be released, significantly reducing peak memory
 * usage. This allows very large SAR documents to be processed without loading
 * the entire HTML document into memory at once.
 *
 * The handler maintains:
 * - A stack of currently open HTML tags.
 * - A table depth counter used to track nested tables.
 *
 * A chunk will only be emitted when:
 * - The buffer size is greater than or equal to `minBufferSize`.
 * - The current table depth is zero.
 *
 * Restricting emission to a table depth of zero ensures that HTML is never
 * emitted part-way through a table.
 *
 * Before emitting a chunk, any currently open tags are temporarily closed so
 * that the emitted fragment is valid HTML. After emission the buffer is reset
 * and the open tags are reintroduced, allowing processing to continue while
 * preserving document context.
 *
 * The buffer size is validated after each parser event. If the buffer exceeds
 * `MAX_BUFFER_SIZE`, processing fails immediately. This is an intentional
 * safeguard designed to detect documents that violate the assumptions of this
 * implementation and to prevent reintroducing the excessive memory consumption
 * that this component is intended to avoid.
 *
 * Design assumptions:
 * - SAR HTML consists primarily of tables.
 * - Nested tables are supported.
 * - Individual tables are expected to remain smaller than `MAX_BUFFER_SIZE`.
 *
 * If a single table exceeds `MAX_BUFFER_SIZE`, processing fails and the source
 * HTML should be investigated. Such a case is considered unexpected and
 * indicates that the document structure no longer conforms to the assumptions
 * of this implementation.
 */
class SubjectAccessRequestHtmlHandler(
  val builder: IncrementalHtmlPdfBuilder,
  val imageStore: ImageStore,
  val subjectAccessRequest: SubjectAccessRequest,
  val minBufferSize: Int = 50_000,
  val telemetryClient: TelemetryClient,
) : DefaultHandler() {

  private val openTags: ArrayDeque<HtmlTag> = ArrayDeque()
  private val buffer: StringBuilder = StringBuilder()
  private var tableDepth = 0
  private val styleBuffer = StringBuilder()
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

  internal fun reset() {
    this.buffer.clear()
    this.tableDepth = 0
    this.openTags.clear()
    this.insideStyleTag = false
    this.styleBuffer.clear()
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
    val tagName = requireNonNullQName(qName)

    if (tagName == STYLE_TAG) {
      insideStyleTag = true
      return
    }

    val attributeMap = attributes.toMap(currentQName = tagName)
    if (voidTags.contains(tagName)) {
      buffer.appendVoidTag(tagName, attributeMap)
      return
    }

    openTags.addLast(HtmlTag(tagName, attributeMap))
    buffer.appendOpenTag(qName, attributeMap)
    requireBufferSizeNotExceeded()

    if (qName == TABLE_TAG) {
      incrementTableDepth()
    }
  }

  /**
   * Handle a characters event (text been open/close tags).
   */
  override fun characters(ch: CharArray?, start: Int, length: Int) {
    requireBufferSizeNotExceeded()

    if (insideStyleTag) {
      // Capture the style content in a separate buffer.
      ch?.let {
        styleBuffer.append(it, start, length)
        requiredStyleBufferSizeNotExceeded()
      }
      return
    }

    ch?.let {
      // Append the text to the buffer
      buffer.append(it, start, length)
      requireBufferSizeNotExceeded()
    }
  }

  /**
   * Handle a close tag event.
   */
  override fun endElement(uri: String?, localName: String?, qName: String?) {
    val tagName = requireNonNullQName(qName)

    if (tagName == STYLE_TAG) {
      // Mark the end of the style tag.
      insideStyleTag = false
      return
    }

    if (voidTags.contains(tagName)) {
      return
    }

    if (openTags.isEmpty()) {
      throw subjectAccessRequestHtmlHandlerException("Invalid HTML - no closing tag on stack for <$qName>")
    }

    val poppedValue = openTags.removeLast()
    if (poppedValue.qName != tagName) {
      throw subjectAccessRequestHtmlHandlerException("Unexpected end tag: expected=${poppedValue.qName}, actual=$tagName")
    }

    buffer.appendCloseTag(tagName)
    requireBufferSizeNotExceeded()

    if (qName == TABLE_TAG) {
      decrementTableDepth()
    }

    if (canEmit()) {
      log.info("emitting chunk: {}", buffer.length)
      emitChunkToBuilder()
    }
  }

  override fun endDocument() {
    if (buffer.isNotEmpty()) {
      log.info("emitting final chunk: {}", buffer.length)
      emitFinalChunkToBuilder()
    }
    log.info("end document {}, tableDepth: {}", openTags.size, tableDepth)
  }

  /**
   * Send the content of the buffer to the builder.
   */
  internal fun emitChunkToBuilder() {
    // Close any remaining open tags to ensure the HTML is properly formatted but keep the tags on the queue so the next
    // chunk is prefixed with the current document context
    for (i in openTags.size - 1 downTo 0) {
      val tag = openTags[i]
      buffer.appendCloseTag(tag.qName)
    }

    // Emit the buffer content to builder and clear buffer.
    builder.append(convertBufferToHtmlString())
    buffer.clear()

    // Populate cleared buffer with any open tags to ensure the document content is retained in the next chunk.
    for (i in openTags.indices) {
      val tag = openTags[i]
      buffer.appendOpenTag(tag.qName, tag.attributes)
    }
  }

  private fun emitFinalChunkToBuilder() {
    if (tableDepth != 0) {
      throw subjectAccessRequestHtmlHandlerException(
        message = "invalid html: Document processing ended with tableDepth > 0: actual=$tableDepth",
      )
    }

    // Close any remaining open tags to ensure the HTML is properly formatted
    while (openTags.isNotEmpty()) {
      val tag = openTags.removeLast()
      buffer.appendCloseTag(tag.qName)
    }

    // emit to consumer and clear buffer.
    builder.append(convertBufferToHtmlString())
    buffer.clear()
  }

  private fun incrementTableDepth() {
    tableDepth++
  }

  private fun decrementTableDepth() {
    tableDepth--
    if (tableDepth < 0) throw RuntimeException("Invalid HTML: Table depth is negative")
  }

  private fun requireBufferSizeNotExceeded() {
    if (buffer.length >= MAX_BUFFER_SIZE) {
      throw subjectAccessRequestHtmlHandlerException(
        message = "buffer size exceeded max limit exiting to prevent potential memory error",
      )
    }
  }

  private fun requiredStyleBufferSizeNotExceeded() {
    if (styleBuffer.length >= MAX_STYLE_SIZE) {
      throw subjectAccessRequestHtmlHandlerException(
        message = "style buffer size exceeded max limit exiting to prevent potential memory error",
      )
    }
  }

  private fun requireNonNullQName(
    qName: String?,
  ): String = qName?.takeIf { it.isNotBlank() }
    ?: throw subjectAccessRequestHtmlHandlerException("Invalid HTML: tag name was null or empty")
      .also {
        telemetryClient.trackSarEvent(
          event = ProcessingEvent.HTML_STREAMING_HANDLER_ERROR,
          subjectAccessRequest = subjectAccessRequest,
          Pair("cause", "Invalid HTML: tag name was null or empty"),
        )
      }

  /**
   * Check if we can emit a chunk to the builder. Emit only when the tableDepth tracker is at 0 and the buffer size is
   * the greater than the minimum size.
   */
  private fun canEmit(): Boolean = tableDepth == 0 && buffer.length >= minBufferSize

  internal fun currentBuffer() = buffer.toString()

  internal fun currentStyleBuffer() = styleBuffer.toString()

  internal fun currentOpenTags() = openTags.toList()

  internal fun currentTableDepth() = tableDepth

  internal fun isInsideStyleTag() = insideStyleTag

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
      .append(styleBuffer)
      .appendCloseTag(STYLE_TAG)
      .append(buffer)
  }

  fun Attributes?.toMap(currentQName: String): Map<String, String> = this?.let { attributes ->
    buildMap(attributes.length) {
      for (i in 0 until attributes.length) {
        val attrName = attributes.getQName(i)
        val attrValue = attributes.getValue(i)

        if (isNonEmptyEncodedImage(currentQName, attrName, attrValue)) {
          putImageSrcAttribute(attrName, attrValue)
        } else {
          put(attrName, attrValue)
        }
      }
    }
  } ?: emptyMap()

  private fun isNonEmptyEncodedImage(
    qName: String,
    attrName: String,
    value: String?,
  ): Boolean = "img" == qName
    && "src" == attrName
    && !value.isNullOrEmpty()
    && value.startsWith("data:image/")
    && ";base64," in value

  // TODO need to handle errors here.
  private fun decodeEmbeddedImage(attrValue: String): EmbeddedImage {
    val matches = Regex("^data:(image/.+);base64,(.*)$").find(attrValue)
    val mimeType = matches?.let { it.groupValues[1] }?: ""
    val src = matches?.let { it.groupValues[2] }?: ""
    val extension = MimeTypes.getDefaultMimeTypes().forName(mimeType).extension

    return EmbeddedImage(src, extension)
  }

  private fun MutableMap<String, String>.putImageSrcAttribute(attrName: String, attrValue: String) {
    try {
      val imagePath = imageStore.add(decodeEmbeddedImage(attrValue)).toString()
      put(attrName, imagePath)
    } catch (e: IllegalArgumentException) {
      log.error("error converting attribute value {} to base64, applying storing attribute handling", attrValue, e)
      put(attrName, attrValue)
    }
  }

  data class HtmlTag(val qName: String, val attributes: Map<String, String>)

  fun subjectAccessRequestHtmlHandlerException(
    message: String,
  ) = FatalSubjectAccessRequestException(
    message = message,
    cause = null,
    subjectAccessRequest = subjectAccessRequest,
    event = ProcessingEvent.HTML_STREAMING_HANDLER,
    errorCode = HTML_STREAM_PARSING_ERROR,
  )
}
