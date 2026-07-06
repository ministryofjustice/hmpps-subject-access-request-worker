package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.attachments

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.PdfRenderRequest
import java.nio.file.Path

class ImagePdfRendererIntTest : BasePdfRendererIntTest() {

  @ParameterizedTest
  @CsvSource(
    value = [
      "map.jpg, image/jpeg, image-jpeg.pdf",
      "map.png, image/png, image-png.pdf",
      "map.gif, image/gif, image-gif.pdf",
      "map.tif, image/tiff, image-tiff.pdf",
      "small.jpg, image/jpeg, image-small-jpeg.pdf",
      "large.jpg, image/jpeg, image-large-jpeg.pdf",
      "narrow.jpg, image/jpeg, image-narrow-jpeg.pdf",
      "wide.jpg, image/jpeg, image-wide-jpeg.pdf",
    ],
  )
  fun `should render images in attachments section`(imageFilename: String, contentType: String, expectedOutputPdf: String) = runBlocking {
    val sar = IntegrationTestFixture.createSubjectAccessRequestForService(getServiceConfiguration(), Status.Pending)
    storeEmptyHtml(sar)
    storeAttachment(sar, imageFilename, contentType)

    val reportDir = tempDirectoryService.create("${sar.id}_")

    val pdfPath = pdfServiceV2.renderSubjectAccessRequestPdf(PdfRenderRequest(sar, "John Smith", reportDir))
    assertThat(pdfPath).exists()

    assertAttachmentPdfMatchesExpected(pdfPath, imageFilename, expectedOutputPdf)
  }

  private fun assertAttachmentPdfMatchesExpected(pdfPath: Path, imageFilename: String, expectedFilename: String) {
    val expected = getPreGeneratedPdfDocument("attachments/$expectedFilename").getPage(1)

    getFileInputStream(pdfPath).use { inputStream ->
      val actual = pdfDocumentFromInputStream(inputStream).getPage(5)
      val actualPageText = PdfTextExtractor.getTextFromPage(actual, SimpleTextExtractionStrategy())

      assertThat(actualPageText).`as`("attachment pdf text").contains("Attachment: 1").contains("$imageFilename - Test attachment file $imageFilename")
      assertThat(actual.contentBytes).`as`("attachment pdf bytes").isEqualTo(expected.contentBytes)
    }
  }
}
