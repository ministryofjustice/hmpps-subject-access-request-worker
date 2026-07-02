package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.util.Stack

interface HtmlChunkConsumer {

  fun consume(chunk: String)
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
    currentChunk.append("\n").append("<$qName").append(getTagAttributes(attributes)).append(">")

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
    openTags.pop()

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

  private fun getTagAttributes(attributes: Attributes?): String {
    val attributeMap = mutableMapOf<String, MutableList<String>>()
    val attributesBuilder = StringBuilder()

    attributes?.let {
      for (i in 0 until it.length) {
        val attributeValues = attributeMap[it.getQName(i)] ?: mutableListOf()
        attributeValues.add(it.getValue(i))
        attributeMap[it.getQName(i)] = attributeValues
      }
    }

    attributeMap.forEach { (attributeName, values) ->
      if (values.isNotEmpty()) {
        attributesBuilder.append(" $attributeName=\"${values.joinToString(" ")}\"")
      }
    }
    return attributesBuilder.toString()
  }
}

