package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.attachments

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.BaseProcessorIntTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.NoSchedulingConfig
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.S3TestUtils
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.PdfService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils.TemplateTestingUtil.Companion.getFormattedReportGenerationDate
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

private const val SERVICE_NAME = "create-and-vary-a-licence-api"

@TestPropertySource(
  properties = [
    "html-renderer.enabled=true",
  ],
)
@Import(S3TestUtils::class, NoSchedulingConfig::class)
class ImagePdfRendererIntTest : BaseProcessorIntTest() {

  @MockitoBean
  protected lateinit var dateService: DateService

  @Autowired
  protected lateinit var s3TestUtil: S3TestUtils

  @Autowired
  private lateinit var pdfService: PdfService

  private var attachmentNumber = 1

  @BeforeEach
  fun setup() {
    /** Ensure the test generated reports have the same 'report generation date' as pre-generated reference reports */
    whenever(dateService.reportGenerationDate())
      .thenReturn(getFormattedReportGenerationDate())
    whenever(dateService.reportDateFormat(LocalDate.of(2025, 1, 1)))
      .thenReturn("1 January 2025")
    whenever(dateService.reportDateFormat(LocalDate.of(2024, 1, 1), "Start of record"))
      .thenReturn("1 January 2024")

    attachmentNumber = 1
  }

  @AfterEach
  fun cleanup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()
    s3TestUtil.clearBucket()
  }

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
    val sar = IntegrationTestFixture.createSubjectAccessRequestForService(SERVICE_NAME, Status.Pending)
    storeEmptyHtml(sar)
    storeAttachment(sar, imageFilename, contentType)

    val renderedPdfBytes = pdfService.renderSubjectAccessRequestPdf(PdfService.PdfRenderRequest(sar, "John Smith"))

    assertAttachmentPdfMatchesExpected(renderedPdfBytes, imageFilename, expectedOutputPdf)
  }

  private fun assertAttachmentPdfMatchesExpected(actualPdfBytes: ByteArrayOutputStream, imageFilename: String, expectedFilename: String) {
    val expected = getPreGeneratedPdfDocument("attachments/$expectedFilename").getPage(1)
    val actual = pdfDocumentFromInputStream(ByteArrayInputStream(actualPdfBytes.toByteArray())).getPage(5)
    val actualPageText = PdfTextExtractor.getTextFromPage(actual, SimpleTextExtractionStrategy())

    assertThat(actualPageText).`as`("attachment pdf text").contains("Attachment: 1").contains("$imageFilename - Test attachment file $imageFilename")
    assertThat(actual.contentBytes).`as`("attachment pdf bytes").isEqualTo(expected.contentBytes)
  }

  private fun storeEmptyHtml(sar: SubjectAccessRequest) = runBlocking {
    val documentKey = "${sar.id}/$SERVICE_NAME.html"
    s3TestUtil.putFile(S3TestUtils.S3File(documentKey, "EMPTY"))
    assertThat(s3TestUtil.documentExists(documentKey)).isTrue()
  }

  fun storeAttachment(sar: SubjectAccessRequest, filename: String, contentType: String) = runBlocking {
    val documentKey = "${sar.id}/$SERVICE_NAME/attachments/$filename"
    val content = getAttachmentBytes(filename)
    s3TestUtil.putFile(
      S3TestUtils.S3AttachmentFile(
        key = documentKey,
        content = content,
        contentType = contentType,
        contentLength = content.size.toLong(),
        filename = filename,
        attachmentNumber = 1,
        name = "Test attachment file $filename",
      ),
    )
    assertThat(s3TestUtil.documentExists(documentKey)).isTrue()
  }

  fun getAttachmentBytes(filename: String): ByteArray = this::class.java
    .getResourceAsStream("/integration-tests/attachments/$filename").use { it?.readAllBytes()!! }
}
