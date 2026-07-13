package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking

import nu.validator.htmlparser.sax.HtmlParser
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Path

class HtmlStyleSheetExtractor(val htmlInput: InputStream) {

  constructor(htmlInput: Path) : this(FileInputStream(htmlInput.toFile()))

  fun getStyleSheet(): String {
    val styleSheetParser = HtmlParser().apply { contentHandler = HtmlStyleSheetHandler() }

    // Extract the stylesheet from the document first.
    var styleSheetValue: String? = null
    try {
      styleSheetParser.parse(InputSource(htmlInput))
    } catch (e: HtmlStyleSheetHandler.ParsingCompleteException) {
      styleSheetValue = (styleSheetParser.contentHandler as HtmlStyleSheetHandler).getStyleSheet()
    }

    return styleSheetValue ?: ""
  }

  private class HtmlStyleSheetHandler : DefaultHandler() {
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
        // pretty gross but it's the only way to break out of the streaming process.
        throw ParsingCompleteException("successfully extracted stylesheet")
      }
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
      ch?.takeIf {isStyleSheet }?.let {  styleSheet.append(ch!!, start, length)}
    }

    fun getStyleSheet(): String = styleSheet.toString()
  }
}