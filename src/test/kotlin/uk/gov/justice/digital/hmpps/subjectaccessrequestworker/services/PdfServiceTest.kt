package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils.TemplateTestingUtil
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil.Companion.assertPdfContentMatchesExpected
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil.Companion.createPdfDocumentFromBytes
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil.Companion.getPdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil.Companion.getResource

@ExtendWith(MockitoExtension::class)
class PdfServiceTest {
  private val serviceConfiguration: ServiceConfigurationService = mock()
  private val htmlDocumentStoreService: HtmlDocumentStoreService = mock()
  private val dateService: DateService = mock()

  private val pdfService = PdfService(
    serviceConfiguration,
    htmlDocumentStoreService,
    dateService,
  )

  @ParameterizedTest
  @MethodSource("generateReportTestCases")
  fun `should generate the expected PDF`(testCase: TestCase) = runTest {
    val pdfRenderRequest = PdfService.PdfRenderRequest(
      subjectAccessRequest = IntegrationTestFixture.createSubjectAccessRequestForService(testCase.serviceName),
      subjectName = IntegrationTestFixture.subjectName,
    )

    whenever(htmlDocumentStoreService.getDocument(pdfRenderRequest.subjectAccessRequest, testCase.serviceName))
      .thenReturn(getResource("/integration-tests/html-stubs/${testCase.serviceName}-expected.html"))

    whenever(serviceConfiguration.getSelectedServices(any())).thenReturn(
      listOf(dpsService(testCase.serviceName, testCase.serviceLabel)),
    )

    whenever(dateService.reportGenerationDate())
      .thenReturn(TemplateTestingUtil.getFormattedReportGenerationDate())

    val actual = pdfService.renderSubjectAccessRequestPdf(pdfRenderRequest)

    val actualPdf = createPdfDocumentFromBytes(actual)
    val expectedPdf = getPdfDocument("/integration-tests/reference-pdfs/${testCase.serviceName}-reference.pdf")

    assertPdfContentMatchesExpected(actualPdf, expectedPdf)
  }

  private fun dpsService(serviceName: String, serviceLabel: String) = DpsService(
    url = "http://localhost:8080/",
    name = serviceName,
    orderPosition = 1,
    businessName = serviceLabel,
  )

  companion object {
    @JvmStatic
    fun generateReportTestCases() = listOf(
      TestCase(
        serviceName = "hmpps-book-secure-move-api",
        serviceLabel = "Book a Secure Move",
      ),
    )

    data class TestCase(val serviceName: String, val serviceLabel: String)
  }
}