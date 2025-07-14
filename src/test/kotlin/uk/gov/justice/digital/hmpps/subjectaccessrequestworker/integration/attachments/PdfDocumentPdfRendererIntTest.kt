package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.attachments

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.PdfService
import java.io.ByteArrayInputStream

class PdfDocumentPdfRendererIntTest : BasePdfRendererIntTest() {

  @ParameterizedTest
  @CsvSource(
    value = [
      "doc.pdf, pdf-A4.pdf, 3",
      "doc-a5.pdf, pdf-A5.pdf, 3",
      "doc-a3.pdf, pdf-A3.pdf, 3",
      "doc-landscape.pdf, pdf-landscape.pdf, 4",
      "doc-1page.pdf, pdf-1page.pdf, 1",
      "doc-10page.pdf, pdf-10page.pdf, 10",
    ],
  )
  fun `should render pdfs in attachments section`(filename: String, expectedFilename: String, numPages: Int) = runBlocking {
    val sar = IntegrationTestFixture.createSubjectAccessRequestForService(SERVICE_NAME, Status.Pending)
    storeEmptyHtml(sar)
    storeAttachment(sar, filename, "application/pdf")

    val renderedPdfBytes = pdfService.renderSubjectAccessRequestPdf(PdfService.PdfRenderRequest(sar, "John Smith"))

    val actualPdfDoc = pdfDocumentFromInputStream(ByteArrayInputStream(renderedPdfBytes.toByteArray()))

    val attachmentInfoPage = PdfTextExtractor.getTextFromPage(actualPdfDoc.getPage(5), SimpleTextExtractionStrategy())
    assertThat(attachmentInfoPage).`as`("attachment info page text")
      .contains("Attachment: 1")
      .contains("$filename - Test attachment file $filename")
      .contains("Attachment PDF content follows on subsequent $numPages page(s)")

    val expectedPdfDoc = getPreGeneratedPdfDocument("attachments/$expectedFilename")
    for (pageNum in 1..numPages) {
      assertThat(actualPdfDoc.getPage(pageNum + 5).contentBytes).`as`("attachment page $pageNum pdf bytes")
        .isEqualTo(expectedPdfDoc.getPage(pageNum).contentBytes)
    }
  }
}
