package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.PdfRenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil
import java.nio.file.Path
import java.util.UUID

class IncrementalHtmlPdfBuilderTest {

  @TempDir
  lateinit var tempDir: Path

  private val serviceConfiguration: ServiceConfiguration = mock()
  private val subjectAccessRequest: SubjectAccessRequest = mock()
  private val imageStore: ImageStore = mock()

  private lateinit var pdfRenderRequest: PdfRenderRequest

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(IncrementalHtmlPdfBuilderTest::class.java)
  }

  @BeforeEach
  fun setup() {
    whenever(serviceConfiguration.serviceName).thenReturn("test-service")
    whenever(subjectAccessRequest.nomisId).thenReturn("A1234AA")

    val rootDir = tempDir.resolve(UUID.randomUUID().toString())

    pdfRenderRequest = PdfRenderRequest(
      subjectAccessRequest = subjectAccessRequest,
      subjectName = "WOW, Dougal",
      reportDir = rootDir,
    )
  }

  @Test
  fun `should generate expected pdf from html chunk`() {
    IncrementalHtmlPdfBuilder(
      pdfRenderRequest = pdfRenderRequest,
      serviceConfiguration = serviceConfiguration,
      imageStore = imageStore,
    ).use { it.append(PdfTestUtil.getResourceAsString("/pdf/input-html/test-service-data.html")) }

    val actualPdfPath = pdfRenderRequest.serviceDataPdfPath(serviceConfiguration)
    val expectedPdfPath = PdfTestUtil.getResourcePath("/pdf/output-pdf/test-service-data-expected.pdf")

    PdfTestUtil.assertPdfsVisuallyEquivalent(expectedPdfPath, actualPdfPath)
  }
}
