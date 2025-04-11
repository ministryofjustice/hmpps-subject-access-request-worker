package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import org.assertj.core.api.Assertions.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

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
        val expectedPageN = PdfTextExtractor.getTextFromPage(expected.getPage(i), SimpleTextExtractionStrategy())

        assertThat(actualPageN)
          .isEqualTo(expectedPageN)
          .withFailMessage("actual page: $i did not match expected.")
      }
    }
  }
}
