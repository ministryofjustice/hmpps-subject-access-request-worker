package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getInputStream
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getReadablePdfDocument
import java.nio.file.Path

class ITextServicePdfRendererTest {

  @TempDir
  lateinit var tempDir: Path

  private val renderer = ITextServicePdfRenderer()

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
      <div style="page-break-before: always;"></div>
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
  fun `should render an image element to the pdf`() = runTest {
    val base64Png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    val html = """<img src="data:image/png;base64,$base64Png" style="display:block;" />"""
    val outputPath = tempDir.resolve("service.pdf")

    renderer.generateServicePdf(outputPath, html.byteInputStream())

    getReadablePdfDocument(getInputStream(outputPath)).use { pdf ->
      assertThat(pdf.numberOfPages).isEqualTo(1)
      val imageResources = pdf.getPage(1).resources.getResourceNames(PdfName.XObject)
      assertThat(imageResources).isNotEmpty()
    }
  }

  private fun pageText(pdf: PdfDocument, pageNumber: Int) = PdfTextExtractor.getTextFromPage(pdf.getPage(pageNumber), SimpleTextExtractionStrategy())
}
