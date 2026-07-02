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
  "/Users/david.llewellyn/development/hmpps-subject-access-request-worker/src/test/resources/pdfTest/service2.html"

fun main(args: Array<String>) {
  HtmlChunkPdfConsumer().use { consumer ->
    val parser = HtmlParser().apply { contentHandler = HtmlStreamingHandler(consumer) }
    parser.parse(InputSource(FileInputStream(Paths.get(HTML_PATH).toFile())))
  }
}

class HtmlStreamingHandler(val chunkConsumer: HtmlChunkConsumer) : DefaultHandler() {

  private val openTags: Stack<String> = Stack()
  private val currentChunk: StringBuilder = StringBuilder()
  private val skipTags = listOf("html", "body", "head")
  private var tableDepth = 0

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

      if (currentChunk.isNotEmpty() && tableDepth == 0) {
        while (openTags.isNotEmpty()) {
          val tag = openTags.pop()
          currentChunk.append("</$tag>")
        }
        chunkConsumer.consume(currentChunk.toString())
        currentChunk.clear()
      }
    }
  }

  override fun endDocument() {
    if (currentChunk.isNotEmpty()) {
      println("sending remaining chunk")
      chunkConsumer.consume(currentChunk.toString())
      currentChunk.clear()
    }

    chunkConsumer.consume("\n</body>\n</html>\n")
    println("end: Document ${openTags.size}, tableDepth=$tableDepth")
  }
}

interface HtmlChunkConsumer {

  fun consume(chunk: String)
}
