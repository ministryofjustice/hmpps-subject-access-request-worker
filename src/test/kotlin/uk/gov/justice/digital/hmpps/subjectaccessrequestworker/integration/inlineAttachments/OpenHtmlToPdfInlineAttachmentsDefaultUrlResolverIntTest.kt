package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.inlineAttachments

import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.REFERENCE_PDF_BASE_DIR
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Test verifies expected PDF is generated when OpenHtmlToPdf service renderer is configured and the default URL
 * retriever is configured.
 */
@TestPropertySource(
  properties = [
    "application.service-renderer=openhtmltopdf",
    "application.pdf-uri-retriever=default",
  ],
)
class OpenHtmlToPdfInlineAttachmentsDefaultUrlResolverIntTest : BaseProcessorInlineAttachmentsIntTest() {
  override val referencePdfBaseDir: String = "${REFERENCE_PDF_BASE_DIR}/openhtmltopdf"

  @Test
  fun `should render the expected PDF using the default PDF URI retriever when HTML contains embedded inline attachments`() {
    val serviceConfig = createServiceConfiguration(
      serviceName = "mock-service-openhtmltopdf-embedded-inline-attachments",
      label = "Wiremock Test Service",
    )

    val templateVersion = createTemplateVersion(
      serviceConfiguration = serviceConfig,
      fileHash = "cebd3e5ba1d6da7fdda3a20a0eaea1e8886df69b6c95db5b0c877730ec87842f",
      version = 13,
    )
    whenever(dateService.reportGenerationDate())
      .thenReturn("25 November 2026")

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
}
