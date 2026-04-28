package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfStream
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import org.assertj.core.api.Assertions.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

class PdfTestUtil {

  companion object {

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
    fun createPdfDocumentFromBytes(bytes: ByteArray): PdfDocument = PdfDocument(PdfReader(ByteArrayInputStream(bytes)))

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
  }
}

data class PdfImageInfo(
  val width: Float,
  val height: Float,
  val hash: String,
)
