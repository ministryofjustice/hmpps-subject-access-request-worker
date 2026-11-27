package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.inlineAttachments

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.REFERENCE_PDF_BASE_DIR
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Test verifies expected PDF is generated when IText service renderer is configured and the default URL retriever is
 * configured.
 */
@TestPropertySource(
  properties = [
    "application.service-renderer=itext",
    "application.pdf-uri-retriever=default",
  ],
)
class ItextInlineAttachmentsDefaultUrlResolverIntTest : BaseProcessorInlineAttachmentsIntTest() {
  override val referencePdfBaseDir: String = "${REFERENCE_PDF_BASE_DIR}/itext"

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
