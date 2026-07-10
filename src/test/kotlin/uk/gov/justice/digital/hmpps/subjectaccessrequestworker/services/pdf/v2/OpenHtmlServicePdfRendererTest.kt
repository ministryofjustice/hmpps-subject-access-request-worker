package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getInputStream
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getReadablePdfDocument
import java.nio.file.Path

class OpenHtmlServicePdfRendererTest {

  @TempDir
  lateinit var tempDir: Path

  private val renderer = OpenHtmlServicePdfRenderer()

  @Test
  fun `should render html content to a valid pdf`() = runTest {
    val outputPath = tempDir.resolve("service.pdf")

    renderer.generateServicePdf(outputPath, "<h1>Test Service Report</h1>".byteInputStream())

    assertThat(outputPath).exists()
    getReadablePdfDocument(getInputStream(outputPath)).use { pdf ->
      assertThat(pdf.numberOfPages).isEqualTo(1)
      assertThat(pageText(pdf, 1)).contains("Test Service Report")
    }
  }

  @Test
  fun `should start a new page when the html contains a page break`() = runTest {
    val html = """
      <h1>Page one</h1>
      <div class="page-break"></div>
      <h1>Page two</h1>
    """.trimIndent()
    val outputPath = tempDir.resolve("service.pdf")

    renderer.generateServicePdf(outputPath, html.byteInputStream())

    getReadablePdfDocument(getInputStream(outputPath)).use { pdf ->
      assertThat(pdf.numberOfPages).isEqualTo(2)
      assertThat(pageText(pdf, 1)).contains("Page one")
      assertThat(pageText(pdf, 2)).contains("Page two")
    }
  }

  @Test
  fun `should strip style tags from the html and apply them as css rather than rendering their raw content`() = runTest {
    val html = """
      <style>p { color: #ff0000; }</style>
      <h1>Test Service Report</h1>
    """.trimIndent()
    val outputPath = tempDir.resolve("service.pdf")

    renderer.generateServicePdf(outputPath, html.byteInputStream())

    getReadablePdfDocument(getInputStream(outputPath)).use { pdf ->
      assertThat(pdf.numberOfPages).isEqualTo(1)
      val text = pageText(pdf, 1)
      assertThat(text).contains("Test Service Report")
      assertThat(text).doesNotContain("color")
    }
  }

  private fun pageText(pdf: PdfDocument, pageNumber: Int) = PdfTextExtractor.getTextFromPage(pdf.getPage(pageNumber), SimpleTextExtractionStrategy())
}
