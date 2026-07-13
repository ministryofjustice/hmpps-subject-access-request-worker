package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking

import org.jsoup.nodes.Entities
import org.slf4j.LoggerFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.consumer.HtmlChunkConsumer

class HtmlStreamingHandler(
  val chunkConsumer: HtmlChunkConsumer,
  val styleSheet: String,
  val minBufferSize: Int = 50000,
) : DefaultHandler() {

  private val openTags: ArrayDeque<String> = ArrayDeque()
  private val currentChunk: StringBuilder = StringBuilder()
  private val skipTags = listOf("html", "body", "head", "style")
  private var tableDepth = 0
  private var currentTag: String? = null

  private companion object {
    private val log = LoggerFactory.getLogger(HtmlStreamingHandler::class.java)
  }

  override fun startDocument() {
  }

  override fun startElement(
    uri: String?,
    localName: String?,
    qName: String?,
    attributes: Attributes?,
  ) {
    currentTag = qName
    if (qName.isNullOrBlank()) {
      throw RuntimeException("Invalid HTML - tag name was null or empty")
    }

    if (skipTags.contains(qName)) {
      return
    }

    openTags.addLast(qName)
    currentChunk.appendOpenTag(qName, attributes)

    if (qName == "table") {
      incrementTableDepth()
    }
  }

  override fun characters(ch: CharArray?, start: Int, length: Int) {
    if (skipTags.contains(currentTag)) {
      return
    }
    ch?.let { currentChunk.append(it, start, length) }
  }

  override fun endElement(uri: String?, localName: String?, qName: String?) {
    currentTag = null

    if (skipTags.contains(qName)) {
      return
    }

    if (openTags.isEmpty()) {
      throw RuntimeException("Invalid HTML - no closing tag on stack for <$qName>")
    }

    val poppedValue = openTags.removeLast()
    if (poppedValue != qName) {
      throw RuntimeException("Unexpected end tag: expected=$poppedValue, actual=$qName")
    }

    currentChunk.appendCloseTag(qName)

    if (qName == "table") {
      decrementTableDepth()
    }

    if (canEmit()) {
      log.info("emitting chunk: {}", currentChunk.length)
      emitChunkToConsumer()
    }
  }

  override fun endDocument() {
    if (currentChunk.isNotEmpty()) {
      emitChunkToConsumer()
    }
    log.info("end document {}, tableDepth: {}", openTags.size, tableDepth)
  }

  private fun emitChunkToConsumer() {
    val fullHtmlChunk = wrapHtml(currentChunk.toString())
    chunkConsumer.consume(fullHtmlChunk)
    currentChunk.clear()
  }

  private fun incrementTableDepth() {
    tableDepth++
  }

  private fun decrementTableDepth() {
    tableDepth--
    if (tableDepth < 0) throw RuntimeException("Invalid HTML: Table depth is negative")
  }

  private fun canEmit(): Boolean = tableDepth == 0 && openTags.isEmpty() && currentChunk.length >= minBufferSize

  private fun wrapHtml(bodyContent: String): String {
    return """
<!DOCTYPE html>
<html>
  <head>
    <style>
      $styleSheet
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
}

