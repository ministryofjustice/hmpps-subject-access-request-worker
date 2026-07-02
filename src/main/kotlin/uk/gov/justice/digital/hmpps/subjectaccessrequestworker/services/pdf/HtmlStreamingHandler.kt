package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import nu.validator.htmlparser.sax.HtmlParser
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.FileInputStream
import java.nio.file.Paths
import java.util.EmptyStackException
import java.util.Stack

const val HTML_PATH =
  "/Users/david.llewellyn/development/hmpps-subject-access-request-worker/src/test/resources/pdfTest/service1.html"

fun main(args: Array<String>) {
  val styleSheetParser = HtmlParser()
    .apply { contentHandler = HtmlStyleSheetHandler() }

  var styleSheetValue: String? = null

  try {
    styleSheetParser.parse(InputSource(FileInputStream(Paths.get(HTML_PATH).toFile())))
  } catch (e: HtmlStyleSheetHandler.ParsingCompleteException) {
    styleSheetValue = (styleSheetParser.contentHandler as HtmlStyleSheetHandler).getStyleSheet()
  }

  HtmlChunkPdfConsumer().use { consumer ->
    val parser = HtmlParser().apply { contentHandler = HtmlStreamingHandler(consumer, styleSheetValue!!) }
    parser.parse(InputSource(FileInputStream(Paths.get(HTML_PATH).toFile())))
  }
}

class HtmlStreamingHandler(val chunkConsumer: HtmlChunkConsumer, val styleSheet: String) : DefaultHandler() {

  private val openTags: Stack<String> = Stack()
  private val currentChunk: StringBuilder = StringBuilder()
  private val skipTags = listOf("html", "body", "head")
  private var tableDepth = 0
  private val chunkTargetSize = 100000

  override fun startDocument() {
    currentChunk.append("<html>").append("\n").append("<body>").append("\n")
  }

  override fun startElement(
    uri: String?,
    localName: String?,
    qName: String?,
    attributes: Attributes?,
  ) {
    if (skipTags.contains(qName)) {
      return
    }

    openTags.push(qName)
    currentChunk.append("\n").append("<$qName")

    val attributeMap = mutableMapOf<String, MutableList<String>>()
    attributes?.let {
      for (i in 0 until it.length) {
        val attributeValues = attributeMap[it.getQName(i)] ?: mutableListOf()
        attributeValues.add(it.getValue(i))
        attributeMap[it.getQName(i)] = attributeValues
      }
    }

    attributeMap.forEach { (attributeName, values) ->
      if (values.isNotEmpty()) {
        currentChunk.append(" $attributeName=\"${values.joinToString(" ")}\"")
      }
    }

    currentChunk.append(">")

    if (qName == "table") {
      tableDepth++
    }
  }

  override fun characters(ch: CharArray?, start: Int, length: Int) {
    currentChunk.append(ch?.concatToString()?.trim() ?: "")
  }

  override fun endElement(uri: String?, localName: String?, qName: String?) {
    if (skipTags.contains(qName)) {
      return
    }

    currentChunk.append("</$qName>")
    try {
      openTags.pop()
    } catch (e: EmptyStackException) {
      println(qName)
      throw e
    }

    if (qName == "table") {
      tableDepth--

      if (tableDepth == 0 && currentChunk.length >= chunkTargetSize) {
        println("emitting chunk: ${currentChunk.length}")

        val bodyContent = closeOpenTags()
        val fullHtmlChunk = wrapHtml(bodyContent)
        chunkConsumer.consume(fullHtmlChunk)
        currentChunk.clear()
      }
    }
  }

  override fun endDocument() {
    if (currentChunk.isNotEmpty()) {
      println("sending remaining chunk, size: ${currentChunk.length}")

      val bodyContent = closeOpenTags()
      val fullHtmlChunk = wrapHtml(bodyContent)
      chunkConsumer.consume(fullHtmlChunk)
      currentChunk.clear()
    }

    chunkConsumer.consume("\n</body>\n</html>\n")
    println("end: Document ${openTags.size}, tableDepth=$tableDepth")
  }

  private fun closeOpenTags(): String {
    while (openTags.isNotEmpty()) {
      val tag = openTags.pop()
      currentChunk.append("</$tag>")
    }
    return currentChunk.toString()
  }

  private fun wrapHtml(bodyContent: String): String {
    return """
     <!DOCTYPE html>
      <html>
      <head>
          <style>$styleSheet</style>
      </head>
      <body>$bodyContent</body>
      </html>
    """.trimIndent()
  }
}

class HtmlStyleSheetHandler : DefaultHandler() {
  class ParsingCompleteException(msg: String) : RuntimeException(msg)

  var isStyleSheet = false
  val styleSheet = StringBuilder()

  override fun startElement(
    uri: String?,
    localName: String?,
    qName: String?,
    attributes: Attributes?,
  ) {
    if (qName?.lowercase() == "style") {
      isStyleSheet = true
    }
  }

  override fun endElement(uri: String?, localName: String?, qName: String?) {
    if (isStyleSheet) {
      throw ParsingCompleteException("successfully extracted stylesheet")
    }
  }

  override fun characters(ch: CharArray?, start: Int, length: Int) {
    if (isStyleSheet) {
      styleSheet.append(ch?.concatToString()?.trim() ?: "")
    }
  }

  fun getStyleSheet(): String = styleSheet.toString()
}

interface HtmlChunkConsumer {

  fun consume(chunk: String)
}
