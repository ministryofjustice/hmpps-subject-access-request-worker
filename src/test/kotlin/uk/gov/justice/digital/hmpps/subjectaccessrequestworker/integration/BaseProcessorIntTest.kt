package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.nio.file.Path

@Import(S3TestUtils::class, NoSchedulingConfig::class)
@Testcontainers
class BaseProcessorIntTest : IntegrationTestBase() {

  @TempDir
  lateinit var tempDir: Path

  @Autowired
  protected lateinit var sarProcessor: SubjectAccessRequestProcessor

  @Autowired
  protected lateinit var serviceConfigurationRepository: ServiceConfigurationRepository

  @Autowired
  protected lateinit var templateVersionRepository: TemplateVersionRepository

  @Autowired
  protected lateinit var s3TestUtil: S3TestUtils

  protected val createAndVaryALicenseServiceConfig = createTestServiceConfiguration(
    name = "create-and-vary-a-licence-api",
    label = "Create and Vary a Licence",
  )

  protected val courtCaseServiceServiceConfig = createTestServiceConfiguration(
    name = "court-case-service",
    label = "Prepare a Case for Sentence",
  )

  protected val bookSecureMoveServiceConfig = createTestServiceConfiguration(
    name = "hmpps-book-secure-move-api",
    label = "Book a Secure Move",
  )

  protected val incentivesApiServiceConfig = createTestServiceConfiguration(
    name = "hmpps-incentives-api",
    label = "Incentives",
  )

  protected val managingPrisonerAppsServiceConfig = createTestServiceConfiguration(
    name = "hmpps-managing-prisoner-apps-api",
    label = "Managing prisoner applications",
  )

  protected val educationWorkPlanServiceConfig: ServiceConfiguration = createTestServiceConfiguration(
    name = "hmpps-education-and-work-plan-api",
    label = "Learning and Work Progress",
  )
  protected val nonAssociationsServiceConfig: ServiceConfiguration = createTestServiceConfiguration(
    name = "hmpps-non-associations-api",
    label = "Learning and Work Progress",
  )

  protected val healthAndMedicationServiceConfig = createTestServiceConfiguration(
    name = "hmpps-health-and-medication-api",
    label = "Health and medication",
  )

  protected fun assertUploadedDocumentMatchesExpectedPdf(expectedPath: String, serviceName: String) {
    val referencePdfPath = PdfTestUtil.getResourcePath( expectedPath)
    val generatedPdf = getUploadedPdfDocumentAsFile(
      tempDir.resolve("$serviceName-${System.currentTimeMillis()}-generated.pdf")
    )

    PdfTestUtil.assertPdfsVisuallyEquivalent(
      actual = generatedPdf.toPath(),
      expected = referencePdfPath,
    )
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

  protected fun htmlRendererSuccessfullyRendersHtml(
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
    val serviceHtml = this::class.java.getResourceAsStream(fileToAddToBucket)
      ?.bufferedReader()
      .use { it?.readText() ?: "EMPTY" }

    s3TestUtil.putFile(
      file = S3TestUtils.S3File(documentKey, serviceHtml),
      metadata = mapOf("template_version" to "v$templateVersion"),
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
