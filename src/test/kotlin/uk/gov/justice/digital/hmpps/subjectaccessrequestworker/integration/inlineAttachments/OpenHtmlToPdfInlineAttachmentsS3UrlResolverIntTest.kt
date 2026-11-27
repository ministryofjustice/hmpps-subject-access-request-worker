package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.inlineAttachments

import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.REFERENCE_PDF_BASE_DIR
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Test verifies expected PDF is generated when OpenHtmlToPdf service renderer is configured and the S3 URL retriever is
 * configured. Inline attachments with url host 'sar-inline-attachments' will be retrieved from the S3 bucket. HTML
 * embedded images should continue to be rendered.
 */
@TestPropertySource(
  properties = [
    "application.service-renderer=openhtmltopdf",
    "application.pdf-uri-retriever=s3",
  ],
)
class OpenHtmlToPdfInlineAttachmentsS3UrlResolverIntTest : BaseProcessorInlineAttachmentsIntTest() {
  override val referencePdfBaseDir: String = "${REFERENCE_PDF_BASE_DIR}/openhtmltopdf"

  @Test
  fun `should render the expected PDF using the S3 PDF URI retriever when HTML contains embedded inline attachments`() {
    whenever(dateService.reportGenerationDate())
      .thenReturn("25 November 2026")

    val serviceConfig = createServiceConfiguration(
      serviceName = "mock-service-openhtmltopdf-embedded-inline-attachments",
      label = "Wiremock Test Service",
    )

    val templateVersion = createTemplateVersion(
      serviceConfiguration = serviceConfig,
      fileHash = "cebd3e5ba1d6da7fdda3a20a0eaea1e8886df69b6c95db5b0c877730ec87842f",
      version = 13,
    )

    val sar = createSarWithServiceDetails(
      serviceConfiguration = serviceConfig,
      templateVersion = templateVersion,
      nomisId = "A1234AA",
      dateFrom = null,
      dateTo = LocalDate.of(2026, 11, 25),
      sarCaseReferenceNumber = "DL",
      requestDateTime = LocalDateTime.of(2026, 11, 25, 12, 0, 0),
    )

    executeTest(serviceConfig, sar, 13)
  }

  @Test
  fun `should render the expected PDF using the S3 PDF URI retriever when HTML contains S3 inline attachments URLs`() {
    whenever(dateService.reportGenerationDate())
      .thenReturn("25 November 2026")

    val serviceConfig = createServiceConfiguration(
      serviceName = "mock-service-openhtmltopdf-s3-inline-attachments",
      label = "Wiremock Test Service",
    )

    val templateVersion = createTemplateVersion(
      serviceConfiguration = serviceConfig,
      fileHash = "cebd3e5ba1d6da7fdda3a20a0eaea1e8886df69b6c95db5b0c877730ec87842f",
      version = 13,
    )

    whenever(dateService.reportGenerationDate())
      .thenReturn("26 November 2026")

    addInlineAttachmentsToS3Bucket(
      InlineAttachment(
        serviceConfig = serviceConfig,
        filename = "map.jpg",
        attachmentFilename = "35eab234-d32f-4008-9ad7-9af5aef7e985.jpg",
      ),

      InlineAttachment(
        serviceConfig = serviceConfig,
        filename = "image.png",
        attachmentFilename = "d3e0ace5-3bd5-4f88-bf33-4343c15fd89e.png",
      ),

      InlineAttachment(
        serviceConfig = serviceConfig,
        filename = "map.gif",
        attachmentFilename = "d856863c-067a-4489-adaf-1463b40a3e89.gif",
      ),
    )

    addAttachmentsToS3Bucket(
      Attachment(
        serviceConfig = serviceConfig,
        filename = "map.jpg",
        attachmentFilename = "Map image",
        contentType = "image/jpeg",
        attachmentNumber = 1,
      ),
      Attachment(
        serviceConfig = serviceConfig,
        filename = "doc1.pdf",
        attachmentFilename = "Test document",
        contentType = "application/pdf",
        attachmentNumber = 2,
      ),
      Attachment(
        serviceConfig = serviceConfig,
        filename = "test.mp4",
        attachmentFilename = "Video recording",
        contentType = "video/mp4",
        attachmentNumber = 3,
      ),
      Attachment(
        serviceConfig = serviceConfig,
        filename = "doc1.docx",
        attachmentFilename = "Word document",
        contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        attachmentNumber = 4,
      ),
    )

    val sar = createSarWithServiceDetails(
      serviceConfiguration = serviceConfig,
      templateVersion = templateVersion,
      dateFrom = null,
      dateTo = LocalDate.of(2026, 11, 26),
      nomisId = "A1234AA",
      sarCaseReferenceNumber = "Test1",
      requestDateTime = LocalDateTime.of(2026, 11, 26, 12, 0, 0),
    )

    executeTest(serviceConfig, sar, 13)
  }
}
