package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.inlineAttachments

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.REFERENCE_PDF_BASE_DIR
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Test verifies expected PDF is generated when IText service renderer is configured and the S3 URL retriever is
 * configured. Inline attachments with url host 'sar-inline-attachments' will be retrieved from the S3 bucket. HTML
 * embedded images should continue to be rendered.
 */
@TestPropertySource(
  properties = [
    "application.service-renderer=itext",
    "application.pdf-uri-retriever=s3",
  ],
)
class ItextInlineAttachmentsS3UrlResolverIntTest : BaseProcessorInlineAttachmentsIntTest() {
  override val referencePdfBaseDir: String = "${REFERENCE_PDF_BASE_DIR}/itext"

  @Test
  fun `should render the expected PDF using the S3 PDF URI retriever when HTML contains S3 inline attachments URLs`() = runBlocking {
    val serviceConfig = createServiceConfiguration(
      serviceName = "mock-service-itext-s3-inline-attachments",
      label = "Mock Service iText Inline Attachments",
      templateMigrated = false,
    )

    addInlineAttachmentsToS3Bucket(
      InlineAttachment(
        serviceConfig = serviceConfig,
        filename = "map.jpg",
        attachmentFilename = "35eab234-d32f-4008-9ad7-9af5aef7e985.jpg",
      ),
      InlineAttachment(
        serviceConfig = serviceConfig,
        filename = "large.jpg",
        attachmentFilename = "d3e0ace5-3bd5-4f88-bf33-4343c15fd89e.jpg",
      ),
    )

    val sar = createSarWithServiceDetails(
      serviceConfiguration = serviceConfig,
      templateVersion = null,
      dateFrom = LocalDate.of(2024, 1, 1),
      dateTo = LocalDate.of(2025, 1, 1),
      sarCaseReferenceNumber = "666",
      nomisId = "nomis-666",
      requestDateTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0),
    )

    executeTest(serviceConfig, sar, null)
  }

  @Test
  fun `should render the expected PDF using the S3 PDF URI retriever when HTML contains embedded inline attachments`() = runBlocking {
    val serviceConfig = createServiceConfiguration(
      serviceName = "hmpps-managing-prisoner-apps-api",
      label = "Managing prisoner applications",
      templateMigrated = false,
    )

    val sar = createSarWithServiceDetails(
      serviceConfiguration = serviceConfig,
      templateVersion = null,
      dateFrom = LocalDate.of(2024, 1, 1),
      dateTo = LocalDate.of(2025, 1, 1),
      sarCaseReferenceNumber = "666",
      nomisId = "nomis-666",
      requestDateTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0),
    )

    executeTest(serviceConfig, sar, null)
  }
}
