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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.LocationDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository
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
  protected lateinit var s3TestUtil: S3TestUtils

  protected companion object {
    val replaceWhitespaceRegex = Regex("\\s+")
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

    val actualText = StringBuilder()
    val expectedText = StringBuilder()

    for (i in 1..actual.numberOfPages) {
      actualText.append(actual.getPageTextNoFormatting(i))
      expectedText.append(actual.getPageTextNoFormatting(i))
    }

    assertThat(actualText.toString()).isEqualTo(expectedText.toString())
  }

  private fun PdfDocument.getPageTextNoFormatting(
    pageNumber: Int,
  ): String = PdfTextExtractor.getTextFromPage(
    this.getPage(pageNumber),
    SimpleTextExtractionStrategy(),
  ).replace(replaceWhitespaceRegex, " ")

  protected fun assertAttachmentPageMatchesExpected(
    actualPdfDoc: PdfDocument,
    expectedPdfDoc: PdfDocument,
    pageNumber: Int,
    attachmentNumber: Int,
  ) {
    val expected = actualPdfDoc.getPage(pageNumber)
    val actual = expectedPdfDoc.getPage(pageNumber)
    val actualPageText = PdfTextExtractor.getTextFromPage(actual, SimpleTextExtractionStrategy())

    assertThat(actualPageText).`as`("attachment $attachmentNumber text").contains("Attachment: $attachmentNumber")
    assertThat(actual.contentBytes).`as`("page $pageNumber content bytes").isEqualTo(expected.contentBytes)
  }

  protected fun assertPageMatchesExpected(actualPdfDoc: PdfDocument, expectedPdfDoc: PdfDocument, pageNumber: Int) {
    val expected = actualPdfDoc.getPage(pageNumber)
    val actual = expectedPdfDoc.getPage(pageNumber)
    assertThat(actual.contentBytes).`as`("page $pageNumber content bytes").isEqualTo(expected.contentBytes)
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

  protected fun insertSubjectAccessRequest(serviceName: String, status: Status): SubjectAccessRequest {
    val sar = createSubjectAccessRequestWithStatus(status, serviceName)
    assertSubjectAccessRequestHasStatus(sar, status)
    return sar
  }

  protected fun requestHasStatus(subjectAccessRequest: SubjectAccessRequest, expectedStatus: Status): Boolean {
    val target = getSubjectAccessRequest(subjectAccessRequest.id)
    return expectedStatus == target.status
  }

  protected fun clearDatabaseData() {
    subjectAccessRequestRepository.deleteAll()
    prisonDetailsRepository.deleteAll()
    locationDetailsRepository.deleteAll()
  }

  protected fun populatePrisonDetails() {
    prisonDetailsRepository.saveAndFlush(PrisonDetail("MDI", "MOORLAND (HMP & YOI)"))
    prisonDetailsRepository.saveAndFlush(PrisonDetail("LEI", "LEEDS (HMP)"))
  }

  protected fun populateLocationDetails() {
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "cac85758-380b-49fc-997f-94147e2553ac",
        357591,
        "ASSO A WING",
      ),
    )
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "d0763236-c073-4ef4-9592-419bf0cd72cb",
        357592,
        "ASSO B WING",
      ),
    )
    locationDetailsRepository.saveAndFlush(
      LocationDetail(
        "8ac39ebb-499d-4862-ae45-0b091253e89d",
        27187,
        "ADJ",
      ),
    )
  }

  protected fun getServiceConfiguration(serviceName: String): ServiceConfiguration {
    val serviceConfig = serviceConfigurationRepository.findByServiceName(serviceName)
    assertThat(serviceConfig).isNotNull
    return serviceConfig!!
  }

  protected  fun htmlRendererSuccessfullyRendersHtml(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRenderRequest,
    serviceName: String,
  ) = stubHtmlRendererSuccess(
    sar = sar,
    htmlRenderRequest = htmlRenderRequest,
    serviceName = serviceName,
    fileToAddToBucket = serviceName,
  )

  protected  fun htmlRendererSuccessfullyRendersHtmlNoDataHeld(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRenderRequest,
    serviceName: String,
  ) = stubHtmlRendererSuccess(
    sar = sar,
    htmlRenderRequest = htmlRenderRequest,
    serviceName = serviceName,
    fileToAddToBucket = "$serviceName-no-data",
  )

  protected  fun stubHtmlRendererSuccess(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRenderRequest,
    serviceName: String,
    fileToAddToBucket: String,
  ) = runBlocking {
    val documentKey = htmlDocumentKey(sar, serviceName)

    // Stub the wiremock API response.
    htmlRendererApi.stubRenderResponsesWith(
      htmlRenderRequest,
      rendererSuccessResponse(documentKey),
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
