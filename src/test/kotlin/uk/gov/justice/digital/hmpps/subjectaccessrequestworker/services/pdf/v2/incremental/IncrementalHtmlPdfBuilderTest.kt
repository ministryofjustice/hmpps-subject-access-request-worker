package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.PdfRenderRequest
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.toPath
import kotlin.math.abs

class IncrementalHtmlPdfBuilderTest {

  @TempDir
  lateinit var tempDir: Path

  private val serviceConfiguration: ServiceConfiguration = mock()
  private val subjectAccessRequest: SubjectAccessRequest = mock()

  private lateinit var pdfRenderRequest: PdfRenderRequest

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(IncrementalHtmlPdfBuilderTest::class.java)
  }

  @BeforeEach
  fun setup() {
    whenever(serviceConfiguration.serviceName).thenReturn("test-service")
    whenever(subjectAccessRequest.nomisId).thenReturn("A1234AA")

    val rootDir = tempDir.resolve(UUID.randomUUID().toString())

    pdfRenderRequest = PdfRenderRequest(
      subjectAccessRequest = subjectAccessRequest,
      subjectName = "WOW, Dougal",
      reportDir = rootDir,
    )
  }

  @Test
  fun `should generate expected pdf from html chunk`() {
    IncrementalHtmlPdfBuilder(
      pdfRenderRequest = pdfRenderRequest,
      serviceConfiguration = serviceConfiguration,
    ).use { it.append(getInputHtml("test-service-data")) }

    val actualPdfPath = pdfRenderRequest.serviceDataPdfPath(serviceConfiguration)
    val expectedPdfPath = getResourcePath("test-service-data-expected")

    assertPdfsVisuallyEquivalent(expectedPdfPath, actualPdfPath)
  }

  private fun assertPdfsVisuallyEquivalent(
    expected: Path,
    actual: Path,
  ) {
    Loader.loadPDF(expected.toFile()).use { expectedPdf ->
      Loader.loadPDF(actual.toFile()).use { actualPdf ->

        assertThat(expectedPdf.numberOfPages)
          .withFailMessage("actual pdf page count did not match expected")
          .isEqualTo(actualPdf.numberOfPages)

        val expectedRenderer = PDFRenderer(expectedPdf)
        val actualRenderer = PDFRenderer(actualPdf)
        assertImagePixelsEqual(expectedRenderer, actualRenderer, actualPdf)
      }
    }
  }

  private fun assertImagePixelsEqual(
    expectedRenderer: PDFRenderer,
    actualRenderer: PDFRenderer,
    actualPdf: PDDocument,
  ) {
    for (pageIndex in 0 until actualPdf.numberOfPages) {
      val actualImage = actualRenderer.renderImageWithDPI(pageIndex, 300f)
      val expectedImage = expectedRenderer.renderImageWithDPI(pageIndex, 300f)

      val widthDiff = abs(expectedImage.width - actualImage.width)
      assertThat(widthDiff)
        .withFailMessage("actual page image width does not match expected")
        .isZero

      val heightDiff = abs(expectedImage.height - actualImage.height)
      assertThat(heightDiff)
        .withFailMessage("actual page image height does not match expected")
        .isZero

      log.info("comparing page: $pageIndex expected/actual pixel values")

      for (x in 0 until actualImage.width) {
        for (y in 0 until actualImage.height) {
          val expectedPixel = expectedImage.getRGB(x, y)
          val actualPixel = actualImage.getRGB(x, y)

          assertThat(actualPixel)
            .withFailMessage("page $pageIndex: pixels ($x,$y) mismatch, actual: $actualPixel, expected: $expectedPixel")
            .isEqualTo(expectedPixel)
        }
      }
    }
  }

  private fun getInputHtml(
    filename: String,
  ): String = this::class.java.getResourceAsStream("/pdf/input-html/$filename.html")
    ?.bufferedReader()
    ?.readText() ?: ""

  private fun getResourcePath(
    filename: String,
  ): Path = this.javaClass.getResource("/pdf/output-pdf/$filename.pdf").toURI().toPath()
}