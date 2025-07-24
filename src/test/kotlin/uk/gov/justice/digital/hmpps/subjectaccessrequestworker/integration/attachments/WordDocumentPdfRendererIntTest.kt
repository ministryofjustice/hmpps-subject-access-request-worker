package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.attachments

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Testcontainers
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.PdfService
import java.io.ByteArrayInputStream

@Testcontainers
class WordDocumentPdfRendererIntTest : BasePdfRendererIntTest() {

  companion object {
    lateinit var gotenberg: GenericContainer<*>

    @JvmStatic
    @BeforeAll
    fun setup() {
      gotenberg = GenericContainer("gotenberg/gotenberg:8").withExposedPorts(3000)
      gotenberg.start()
      System.setProperty("gotenberg-api.url", "http://${gotenberg.host}:${gotenberg.getMappedPort(3000)}")
    }
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "doc.docx, word-doc-A4.pdf, 5",
      "doc-a5.docx, word-doc-A5.pdf, 3",
      "doc-a3.docx, word-doc-A3.pdf, 3",
      "doc-landscape.docx, word-doc-landscape.pdf, 4",
      "doc-1page.docx, word-doc-1page.pdf, 1",
      "doc-10page.docx, word-doc-10page.pdf, 10",
      "doc-image.docx, word-doc-image.pdf, 2",
    ],
  )
  fun `should render word documents in attachments section`(filename: String, expectedFilename: String, numPages: Int) = runBlocking {
    val sar = IntegrationTestFixture.createSubjectAccessRequestForService(SERVICE_NAME, Status.Pending)
    storeEmptyHtml(sar)
    storeAttachment(sar, filename, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

    val renderedPdfBytes = pdfService.renderSubjectAccessRequestPdf(PdfService.PdfRenderRequest(sar, "John Smith"))

    val actualPdfDoc = pdfDocumentFromInputStream(ByteArrayInputStream(renderedPdfBytes.toByteArray()))

    val attachmentInfoPage = PdfTextExtractor.getTextFromPage(actualPdfDoc.getPage(5), SimpleTextExtractionStrategy())
    assertThat(attachmentInfoPage).`as`("attachment info page text")
      .contains("Attachment: 1")
      .contains("$filename - Test attachment file $filename")
      .contains("Attachment Word content follows on subsequent $numPages page(s)")

    val expectedPdfDoc = getPreGeneratedPdfDocument("attachments/$expectedFilename")
    for (pageNum in 1..numPages) {
      assertThat(actualPdfDoc.getPage(pageNum + 5).contentBytes).`as`("attachment page $pageNum pdf bytes")
        .isEqualTo(expectedPdfDoc.getPage(pageNum).contentBytes)
    }
  }
}
