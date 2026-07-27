package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient.HtmlRenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testNomisId
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.TemplateVersionRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled.SubjectAccessRequestProcessor
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Import(S3TestUtils::class, NoSchedulingConfig::class)
@Testcontainers
class BaseProcessorIntTest : IntegrationTestBase() {

  @Autowired
  protected lateinit var sarProcessor: SubjectAccessRequestProcessor

  @Autowired
  protected lateinit var serviceConfigurationRepository: ServiceConfigurationRepository

  @Autowired
  protected lateinit var templateVersionRepository: TemplateVersionRepository

  @Autowired
  protected lateinit var s3TestUtil: S3TestUtils

  protected companion object {
    val replaceWhitespaceRegex = Regex("\\s+")
    private val nomisIdLineRegex = Regex("^NOMIS ID:.*")
    private val ndeliusIdLineRegex = Regex("^nDelius ID:.*")
    private val nameLineRegex = Regex("^Name:.*")
  }

  protected fun assertRequestClaimedAtLeastOnce(subjectAccessRequest: SubjectAccessRequest) {
    val target = getSubjectAccessRequest(subjectAccessRequest.id)
    assertThat(target.claimDateTime).isNotNull()
    assertThat(target.claimAttempts).isGreaterThanOrEqualTo(1)
  }

  protected fun assertUploadedDocumentMatchesExpectedPdf(serviceName: String) {
    val expected = getPreGeneratedPdfDocument("$serviceName-reference.pdf")
    val actual = getUploadedPdfDocument()

    assertUploadedDocumentMatchesExpectedPdf(actual, expected)
  }

  protected fun assertUploadedDocumentMatchesExpectedPdf(actual: PdfDocument, expected: PdfDocument) {
    assertThat(actual.numberOfPages).isEqualTo(expected.numberOfPages)

    val actualPages = actual.canonicalPageText()
    val expectedPages = expected.canonicalPageText()
    assertThat(actualPages).isEqualTo(expectedPages)
  }

  private fun PdfDocument.getPageTextNoFormatting(
    pageNumber: Int,
  ): String = PdfTextExtractor.getTextFromPage(
    this.getPage(pageNumber),
    SimpleTextExtractionStrategy(),
  ).replace(replaceWhitespaceRegex, " ")

  private fun PdfDocument.canonicalPageText(): List<String> = (1..numberOfPages).map { page ->
    canonicalizePageText(PdfTextExtractor.getTextFromPage(getPage(page), SimpleTextExtractionStrategy()))
  }

  private fun canonicalizePageText(text: String): String = text
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .filterNot { it == "Official Sensitive" }
    .filterNot { nameLineRegex.matches(it) }
    .filterNot { nomisIdLineRegex.matches(it) }
    .filterNot { ndeliusIdLineRegex.matches(it) }
    .joinToString(" ")
    .replace(replaceWhitespaceRegex, " ")
    .trim()

  protected fun assertAttachmentPageMatchesExpected(
    actualPdfDoc: PdfDocument,
    expectedPdfDoc: PdfDocument,
    pageNumber: Int,
    attachmentNumber: Int,
  ) {
    val expectedPageText = expectedPdfDoc.getPageTextNoFormatting(pageNumber)
    val actualPageText = actualPdfDoc.getPageTextNoFormatting(pageNumber)

    assertThat(actualPageText).`as`("attachment $attachmentNumber text").contains("Attachment: $attachmentNumber")
    assertThat(actualPageText).`as`("attachment $attachmentNumber page text").isEqualTo(expectedPageText)
  }

  protected fun assertPageMatchesExpected(actualPdfDoc: PdfDocument, expectedPdfDoc: PdfDocument, pageNumber: Int) {
    val actualPageText = canonicalizePageText(
      PdfTextExtractor.getTextFromPage(actualPdfDoc.getPage(pageNumber), SimpleTextExtractionStrategy()),
    )
    val expectedPageText = canonicalizePageText(
      PdfTextExtractor.getTextFromPage(expectedPdfDoc.getPage(pageNumber), SimpleTextExtractionStrategy()),
    )
    assertThat(actualPageText).`as`("page $pageNumber text").isEqualTo(expectedPageText)
  }

  protected fun assertUploadedDocumentMatchesExpectedNoDataHeldPdf(serviceName: String, serviceLabel: String) {
    val actual = getUploadedPdfDocument()

    assertThat(actual.numberOfPages).isEqualTo(5)
    val actualPageContent = PdfTextExtractor.getTextFromPage(actual.getPage(4), SimpleTextExtractionStrategy())
    val expectedPageContent = contentWhenNoDataHeld(serviceLabel, testNomisId)

    assertThat(actualPageContent)
      .isEqualToIgnoringCase(expectedPageContent)
      .withFailMessage("$serviceName report did not match expected")
  }

  protected fun contentWhenNoDataHeld(serviceLabel: String, nomisId: String): String = StringBuilder("$serviceLabel\n")
    .append("No Data Held")
    .append("\n")
    .append("Name: REACHER, Joe ")
    .append("\n")
    .append("NOMIS ID: $nomisId")
    .append("\n")
    .append("Official Sensitive")
    .toString()

  protected fun insertSubjectAccessRequest(serviceConfig: ServiceConfiguration, status: Status): SubjectAccessRequest {
    val sar = createSubjectAccessRequestWithStatus(status, serviceConfig)
    assertSubjectAccessRequestHasStatus(sar, status)
    return sar
  }

  protected fun requestHasStatus(subjectAccessRequest: SubjectAccessRequest, expectedStatus: Status): Boolean {
    val target = getSubjectAccessRequest(subjectAccessRequest.id)
    return expectedStatus == target.status
  }

  protected fun getServiceConfiguration(serviceName: String): ServiceConfiguration {
    val serviceConfig = serviceConfigurationRepository.findByServiceName(serviceName)
    assertThat(serviceConfig).isNotNull
    return serviceConfig!!
  }

  protected fun htmlRendererSuccessfullyRendersHtml(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRenderRequest,
    serviceName: String,
  ) = stubHtmlRendererSuccess(
    sar = sar,
    htmlRenderRequest = htmlRenderRequest,
    serviceName = serviceName,
    fileToAddToBucket = serviceName,
  )

  protected fun htmlRendererSuccessfullyRendersHtmlNoDataHeld(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRenderRequest,
    serviceName: String,
  ) = stubHtmlRendererSuccess(
    sar = sar,
    htmlRenderRequest = htmlRenderRequest,
    serviceName = serviceName,
    fileToAddToBucket = "$serviceName-no-data",
  )

  protected fun stubHtmlRendererSuccess(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRenderRequest,
    serviceName: String,
    fileToAddToBucket: String,
    templateVersion: String = "1",
  ) = runBlocking {
    val documentKey = htmlDocumentKey(sar, serviceName)

    // Stub the wiremock API response.
    htmlRendererApi.stubRenderResponsesWith(
      htmlRenderRequest,
      rendererSuccessResponse(documentKey, templateVersion),
    )

    // Put the expected Html in the bucket for later.
    s3TestUtil.putFile(
      S3TestUtils.S3File(
        documentKey,
        getReportHtmlForService(fileToAddToBucket),
      ),
    )
    assertThat(s3TestUtil.documentExists(documentKey)).isTrue()
  }

  fun getReportHtmlForService(serviceName: String): String = this::class.java
    .getResourceAsStream("/integration-tests/html-stubs/$serviceName-expected.html")
    ?.bufferedReader()
    .use { it?.readText() ?: "EMPTY" }

  fun htmlDocumentKey(sar: SubjectAccessRequest, serviceName: String) = "${sar.id}/$serviceName.html"

  protected fun errorResponseDefinition(
    status: Int,
    errorCode: String? = null,
  ) = ResponseDefinitionBuilder.responseDefinition()
    .withHeader("Content-Type", "application/json")
    .withStatus(status)
    .withBody(
      objectMapper.writeValueAsString(
        ErrorResponse(
          status = status,
          errorCode = errorCode,
          developerMessage = "ErrorCode: $errorCode",
        ),
      ),
    )
}
