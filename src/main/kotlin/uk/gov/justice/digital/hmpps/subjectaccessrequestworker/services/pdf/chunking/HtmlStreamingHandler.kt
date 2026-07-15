package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking

import org.jsoup.nodes.Entities
import org.slf4j.LoggerFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.consumer.HtmlChunkConsumer

const val MAX_BUFFER_SIZE = 1_000_000

class HtmlStreamingHandler(
  val chunkConsumer: HtmlChunkConsumer,
  val minBufferSize: Int = 75000,
) : DefaultHandler() {

  private val openTags: ArrayDeque<String> = ArrayDeque()
  private val buffer: StringBuilder = StringBuilder()
  private var tableDepth = 0
  private val style = StringBuilder()
  private var insideStyleTag = false

  private val skipTags = setOf(
    "html",
    "body",
    "head",
  )

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
    private val log = LoggerFactory.getLogger(HtmlStreamingHandler::class.java)
  }

  override fun startDocument() {
    log.info("Starting document")
  }

  override fun startElement(
    uri: String?,
    localName: String?,
    qName: String?,
    attributes: Attributes?,
  ) {
    if (qName.isNullOrBlank()) {
      throw RuntimeException("Invalid HTML - tag name was null or empty")
    }

    if (skipTags.contains(qName)) {
      return
    }

    if (qName == "style") {
      insideStyleTag = true
      return
    }

    if (voidTags.contains(qName)) {
      buffer.appendVoidTag(qName, attributes)
      return
    }

    checkChunkSize()
    openTags.addLast(qName)
    buffer.appendOpenTag(qName, attributes)

    if (qName == "table") {
      incrementTableDepth()
    }
  }

  override fun characters(ch: CharArray?, start: Int, length: Int) {
    if (skipTags.contains(openTags.lastOrNull())) {
      return
    }

    checkChunkSize()

    if (insideStyleTag) {
      ch?.let {
        style.append(it, start, length)
      }
      return
    }

    ch?.let {
      buffer.append(it, start, length)
    }
  }

  override fun endElement(uri: String?, localName: String?, qName: String?) {
    if (skipTags.contains(qName)) {
      return
    }
    if (qName == "style") {
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
    if (poppedValue != qName) {
      throw RuntimeException("Unexpected end tag: expected=$poppedValue, actual=$qName")
    }

    buffer.appendCloseTag(qName)

    if (qName == "table") {
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
      emitChunkToConsumer()
    }
    log.info("end document {}, tableDepth: {}", openTags.size, tableDepth)
  }

  private fun emitChunkToConsumer() {
    val fullHtmlChunk = wrapHtml(buffer.toString())
    chunkConsumer.consume(fullHtmlChunk)
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
      throw RuntimeException("buffer size exceeded max limit - HTML likely contains top level wrapper tag")
    }
  }

  private fun canEmit(): Boolean = tableDepth == 0 && openTags.isEmpty() && buffer.length >= minBufferSize

  private fun wrapHtml(bodyContent: String): String {
    return """
<!DOCTYPE html>
<html>
  <head>
    <style>
      $style
    </style>
  </head>
  <body>$bodyContent</body>
</html>""".trimIndent()
  }

  internal fun StringBuilder.appendOpenTag(qName: String?, attributes: Attributes?) {
    this.append("<$qName")
      .appendAttributes(attributes)
      .append(">")
  }

  internal fun StringBuilder.appendCloseTag(qName: String) {
    this.append("</$qName>")
  }

  internal fun StringBuilder.appendAttributes(attributes: Attributes?): StringBuilder {
    attributes?.let {
      for (i in 0 until it.length) {
        append(" ${it.getQName(i)}=\"")
          .append(Entities.escape(it.getValue(i)))
          .append("\"")
      }
    }
    return this
  }

  internal fun StringBuilder.appendVoidTag(qName: String, attributes: Attributes?) {
    this.append("<$qName")
      .appendAttributes(attributes)
      .append("/>")
  }
}

