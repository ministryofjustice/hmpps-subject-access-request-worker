package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.fail
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.io.path.toPath
import kotlin.math.abs

class PdfTestUtil {

  companion object {

    private val log = LoggerFactory.getLogger(PdfTestUtil::class.java)

    /**
     * Get a PDF Document from the specified path
     */
    fun getPdfDocument(path: String): PdfDocument {
      val inputStream = this::class.java.getResourceAsStream(path)
      assertThat(inputStream).isNotNull
      return PdfDocument(PdfReader(inputStream))
    }

    /**
     * Create a PdfDocument from the provided bytes.
     */
    fun createPdfDocumentFromBytes(outputStream: ByteArrayOutputStream): PdfDocument = PdfDocument(
      PdfReader(ByteArrayInputStream(outputStream.toByteArray())),
    )

    /**
     * Get a class path resource.
     */
    fun getResource(resource: String): InputStream? = this::class.java.getResourceAsStream(resource)

    /**
     * Get a resource as a String
     */
    fun getResourceAsString(
      filename: String,
    ): String = PdfTestUtil::class.java.getResourceAsStream(filename)
      ?.bufferedReader()
      ?.readText() ?: ""

    /**
     * Get the [Path] of a class path resource
     */
    fun getResourcePath(
      filename: String,
    ): Path = Companion::class.java.getResource(filename)?.toURI()?.toPath()
      ?: fail("Resource not found: $filename")

    /**
     * Assert that String content of 2 PDFs matches.
     */
    fun assertPdfContentMatchesExpected(
      actual: PdfDocument,
      expected: PdfDocument,
    ) {
      assertThat(actual.numberOfPages).isEqualTo(expected.numberOfPages)

      for (i in 1..actual.numberOfPages) {
        val actualPageN = PdfTextExtractor.getTextFromPage(actual.getPage(i), SimpleTextExtractionStrategy())
        val actualPageImages = extractImagesFromPage(actual.getPage(i))
        val expectedPageN = PdfTextExtractor.getTextFromPage(expected.getPage(i), SimpleTextExtractionStrategy())
        val expectedPageImages = extractImagesFromPage(expected.getPage(i))

        assertThat(actualPageN)
          .isEqualTo(expectedPageN)
          .withFailMessage("actual page: $i did not match expected text.")
        assertThat(actualPageImages).isEqualTo(expectedPageImages)
          .withFailMessage("actual page: $i did not contain expected images.")
      }
    }

    fun extractImagesFromPage(page: PdfPage): List<PdfImageInfo> = page.resources
      ?.getResource(PdfName.XObject)?.values()
      ?.filter { obj -> obj is PdfStream && obj.getAsName(PdfName.Subtype) == PdfName.Image }
      ?.map { obj ->
        val image = PdfImageXObject(obj as PdfStream)
        val hash =
          MessageDigest.getInstance("SHA-256").digest(image.imageBytes).joinToString("") { "%02x".format(it) }
        PdfImageInfo(width = image.width, height = image.height, hash = hash)
      } ?: emptyList()

    fun assertPdfsVisuallyEquivalent(
      expected: Path,
      actual: Path,
    ) {
      log.debug("comparing {} to {} for visual equality", expected.fileName, actual.fileName)
      PDDocument.load(expected.toFile()).use { expectedPdf ->
        PDDocument.load(actual.toFile()).use { actualPdf ->

          assertThat(expectedPdf.numberOfPages)
            .withFailMessage("actual pdf page count did not match, actual=${actualPdf.numberOfPages}, expected=${expectedPdf.numberOfPages}")
            .isEqualTo(actualPdf.numberOfPages)

          val expectedRenderer = PDFRenderer(expectedPdf)
          val actualRenderer = PDFRenderer(actualPdf)
          assertImagePixelsEqual(expected, actual, expectedRenderer, actualRenderer, actualPdf)
        }
      }
    }

    /**
     * PDF comparison is difficult as 2 visual identical documents can have completely different underlying code.
     * This comparison coverts each page of the expected/actual documents to an image then compares the pixels to ensure
     * it matches. This gives us a thorough method of checking 2 PDFs look identical and bypasses aforementioned issue.
     */
    private fun assertImagePixelsEqual(
      expectedPath: Path,
      actualPath: Path,
      expectedRenderer: PDFRenderer,
      actualRenderer: PDFRenderer,
      actualPdf: PDDocument,
    ) {
      for (pageIndex in 0 until actualPdf.numberOfPages) {
        val actualImage = actualRenderer.renderImageWithDPI(pageIndex, 300f)
        val expectedImage = expectedRenderer.renderImageWithDPI(pageIndex, 300f)

        val widthDiff = abs(expectedImage.width - actualImage.width)
        assertThat(widthDiff)
          .withFailMessage("actual page $pageIndex image width does not match expected: difference=$widthDiff")
          .isZero

        val heightDiff = abs(expectedImage.height - actualImage.height)
        assertThat(heightDiff)
          .withFailMessage("actual page $pageIndex image height does not match expected: difference=$heightDiff")
          .isZero

        log.info(
          "Comparing {}/{} pixels: page {}/{}",
          expectedPath.fileName,
          actualPath.fileName,
          pageIndex + 1,
          actualPdf.numberOfPages,
        )
        assertPagePixelsEqual(expectedImage, actualImage, pageIndex)
      }
    }

    private fun assertPagePixelsEqual(expectedImage: BufferedImage, actualImage: BufferedImage, pageIndex: Int) {
      var errors = 0

      // Quicker than bufferedImage.getRGB(x, y) call
      val expectedPixels = (expectedImage.raster.dataBuffer as DataBufferInt).data
      val actualPixels = (actualImage.raster.dataBuffer as DataBufferInt).data

      for (i in expectedPixels.indices) {
        if (expectedPixels[i] != actualPixels[i]) {
          errors++
        }
      }

      if (errors > 0) {
        val diffImagePath = createPageMismatchDiffImage(expectedImage, actualImage, pageIndex)
        fail(
          """
            Page $pageIndex of actual PDF does not match expected.
            Visual diff: file:///${diffImagePath.toAbsolutePath()}
            Image format from left to right - Expected PDF, Actual PDF, Diff
            """,
        )
      }
    }

    private fun createPageMismatchDiffImage(
      expectedImage: BufferedImage,
      actualImage: BufferedImage,
      pageIndex: Int,
    ): Path {
      val diff = BufferedImage(
        (expectedImage.width * 3) + 2,
        expectedImage.height,
        BufferedImage.TYPE_INT_ARGB,
      )

      val g = diff.createGraphics()
      g.drawImage(expectedImage, 0, 0, null)
      g.drawImage(actualImage, expectedImage.width + 1, 0, null)
      g.dispose()

      // Create a black 1px border between the expected, actual and diff images
      for (y in 0 until expectedImage.height) {
        diff.setRGB(expectedImage.width, y, Color.BLACK.rgb)
        diff.setRGB((expectedImage.width * 2) + 1, y, Color.BLACK.rgb)
      }

      for (x in 0 until actualImage.width) {
        for (y in 0 until actualImage.height) {
          val expectedRgb = expectedImage.getRGB(x, y)
          val actualRgb = actualImage.getRGB(x, y)

          val diffRgbVal = if (expectedRgb == actualRgb) {
            Color.LIGHT_GRAY.rgb
          } else {
            Color.RED.rgb
          }

          diff.setRGB(x + (expectedImage.width + 1 + actualImage.width + 1), y, diffRgbVal)
        }
      }

      val diffPath = Files.createDirectories(Paths.get("build", "test-results", "pdf-diff"))
      val diffFile = diffPath.resolve("page-$pageIndex-expected-actual-diff-${System.currentTimeMillis()}.png")

      ImageIO.write(diff, "png", diffFile.toFile())
      return diffFile
    }
  }
}

data class PdfImageInfo(
  val width: Float,
  val height: Float,
  val hash: String,
)
