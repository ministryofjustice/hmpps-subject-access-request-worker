package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getInputStream
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getReadablePdfDocument
import java.nio.file.Path

class OpenHtmlServicePdfRendererTest {

  @TempDir
  lateinit var tempDir: Path

  private val renderer = OpenHtmlServicePdfRenderer()

  private val subjectAccessRequest: SubjectAccessRequest = mock()

  lateinit var pdfRenderRequest: PdfRenderRequest

  @BeforeEach
  fun setup() {
    whenever(subjectAccessRequest.nomisId).thenReturn("A1234BC")

    pdfRenderRequest = PdfRenderRequest(
      subjectName = "Test User",
      subjectAccessRequest = subjectAccessRequest,
      reportDir = tempDir,
    )
  }

  @Test
  fun `should render html content to a valid pdf`() = runTest {
    val outputPath = tempDir.resolve("service.pdf")

    renderer.generateServicePdf(pdfRenderRequest, outputPath, "<h1>Test Service Report</h1>".byteInputStream())

    assertThat(outputPath).exists()
    getReadablePdfDocument(getInputStream(outputPath)).use { pdf ->
      assertThat(pdf.numberOfPages).isEqualTo(1)
      val page1 = pageText(pdf, 1)
      assertThat(page1).contains("Test Service Report")
      assertContainsHeaderAndFooter(page1)
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

    renderer.generateServicePdf(pdfRenderRequest, outputPath, html.byteInputStream())

    getReadablePdfDocument(getInputStream(outputPath)).use { pdf ->
      assertThat(pdf.numberOfPages).isEqualTo(2)
      val page1 = pageText(pdf, 1)
      val page2 = pageText(pdf, 2)
      assertThat(page1).contains("Page one")
      assertThat(page2).contains("Page two")
      assertContainsHeaderAndFooter(page1)
      assertContainsHeaderAndFooter(page2)
    }
  }

  @Test
  fun `should strip style tags from the html and apply them as css rather than rendering their raw content`() = runTest {
    val html = """
      <style>p { color: #ff0000; }</style>
      <h1>Test Service Report</h1>
    """.trimIndent()
    val outputPath = tempDir.resolve("service.pdf")

    renderer.generateServicePdf(pdfRenderRequest, outputPath, html.byteInputStream())

    getReadablePdfDocument(getInputStream(outputPath)).use { pdf ->
      assertThat(pdf.numberOfPages).isEqualTo(1)
      val text = pageText(pdf, 1)
      assertThat(text).contains("Test Service Report")
      assertThat(text).doesNotContain("color")
      assertContainsHeaderAndFooter(text)
    }
  }

  private fun assertContainsHeaderAndFooter(pageText: String) {
    assertThat(pageText).contains("Name: Test User")
    assertThat(pageText).contains("NOMIS ID: A1234BC")
    assertThat(pageText).contains("Official Sensitive")
  }

  private fun pageText(pdf: PdfDocument, pageNumber: Int) = PdfTextExtractor.getTextFromPage(pdf.getPage(pageNumber), SimpleTextExtractionStrategy())
}
