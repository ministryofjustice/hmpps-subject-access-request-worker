package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testSubjectAccessRequestId
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ProbationApiExtension.Companion.probationApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RequestServiceDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.io.path.readBytes

@TestPropertySource(
  properties = [
    "application.service-renderer=itext_incremental",
//    "application.service-renderer=itext",
  ],
)
@DirtiesContext
class PdfGenerationRegressionIntTest : BaseProcessorIntTest() {

  @MockitoBean
  private lateinit var dateService: DateService

  private val reportGenerationDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  @BeforeEach
  fun setup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()
    templateVersionRepository.deleteAll()
  }

  @AfterEach
  fun cleanup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()
    templateVersionRepository.deleteAll()
    serviceConfigurationRepository.deleteAll()
    s3TestUtil.clearBucket()
  }

  @ParameterizedTest
  @CsvSource(
    value = [
//      "offender-case-notes              | Case notes                          | 1  |  2026-10-20 | Dougal   | WOW       | A1234AA | ",
//      "hmpps-book-secure-move-api       | Book a secure move                  | 27 |  2026-10-20 | Dougal   | WOW       | A1234AA | ",
//      "hmpps-activities-management-api  | Manage activities and appointments  | 3  |  2026-10-21 | Andrew   | SMITH     | A8731DY | ",
//      "make-recall-decision-api         | Consider a recall                   | 5  |  2026-10-21 | Terrence | GREENHOLT |         | X788206 ",
      "wiremock-test-service         | Wiremock Test Service | 13  |  2026-10-26 | Dougal | WOW | A1234AA  | X788206 ",
    ],
    delimiter = '|',
  )
  fun `should generate a PDF visually equal to the expected reference PDF`(
    serviceName: String,
    serviceLabel: String,
    templateVersion: String,
    reportGenerationDate: String,
    subjectFirstName: String,
    subjectLastName: String,
    nomisId: String? = null,
    ndeliusId: String? = null,
  ) = runTest {
    val serviceConfig = setupServiceConfigurationWithTemplateVersion(serviceName, serviceLabel, templateVersion)
    val requestDate = LocalDate.parse(reportGenerationDate, DateTimeFormatter.ISO_DATE)

    val params = setupTestData(
      serviceConfig = serviceConfig,
      templateVersion = templateVersion,
      reportGenerationDate = requestDate,
      subjectFirstName = subjectFirstName,
      subjectLastName = subjectLastName,
      nomisId = nomisId,
      ndeliusId = ndeliusId,
    )

    sarProcessor.execute()
    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestHasStatus(params.sar(), Status.Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, params.htmlRenderRequest())
    documentApi.verifyStoreDocumentIsCalled(1, params.sar().id.toString())

    val subjectId = nomisId ?: ndeliusId
    val referencePdfPath = PdfTestUtil.getResourcePath("/integration-tests/regression/pdf-baseline/$serviceName-$subjectId-itext.pdf")
    val generatedPdf = getUploadedPdfDocumentAsFile(tempDir.resolve("$subjectId-generated.pdf"))

    Files.copy(
      generatedPdf.toPath(),
      PdfTestUtil.getResourcePath("/integration-tests/regression").resolve("$serviceName-test-output-itext.pdf"),
    )

    PdfTestUtil.assertPdfsVisuallyEquivalent(
      actual = generatedPdf.toPath(),
      expected = referencePdfPath,
    )
  }

  @Timeout(20, unit = TimeUnit.MINUTES)
  @ParameterizedTest
  @CsvSource(
    value = [
      "accredited-programmes-custody  | Accredited programmes custody | 1 | 2026-10-20 | Dougal | WOW | A1234AA ",
    ],
    delimiter = '|',
  )
  fun `should generate uber PDF`(
    serviceName: String,
    serviceLabel: String,
    templateVersion: String,
    reportGenerationDate: String,
    subjectFirstName: String,
    subjectLastName: String,
    nomisId: String? = null,
  ) {
    val serviceConfig = setupServiceConfigurationWithTemplateVersion(serviceName, serviceLabel, templateVersion)
    val requestDate = LocalDate.parse(reportGenerationDate, DateTimeFormatter.ISO_DATE)

    val params = setupTestData(
      serviceConfig = serviceConfig,
      templateVersion = templateVersion,
      reportGenerationDate = requestDate,
      subjectFirstName = subjectFirstName,
      subjectLastName = subjectLastName,
      nomisId = nomisId,
      ndeliusId = null,
    )

    runBlocking {
      sarProcessor.execute()
      await()
        .atMost(3, TimeUnit.SECONDS)
        .until { requestHasStatus(params.sar(), Status.Completed) }
    }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, params.htmlRenderRequest())
    documentApi.verifyStoreDocumentIsCalled(1, params.sar().id.toString())

    val generatedPdf = getUploadedPdfDocumentAsFile(tempDir.resolve("$nomisId-generated.pdf"))
    val referencePdfPath = PdfTestUtil.getResourcePath("/integration-tests/regression/pdf-baseline/$serviceName-$nomisId.pdf")

//    Files.copy(
//      generatedPdf.toPath(),
//      PdfTestUtil.getResourcePath("/integration-tests/regression").resolve("$serviceName-test-output-incremental.pdf"),
//    )

    PdfTestUtil.assertPdfsVisuallyEquivalent(
      actual = generatedPdf.toPath(),
      expected = referencePdfPath,
    )
  }

  //@Disabled
  @Timeout(10, unit = TimeUnit.MINUTES)
  @ParameterizedTest
  @CsvSource(
    value = [
      "wiremock-test-service  | Wiremock test service | 1 | 2026-10-20 | Dougal | WOW | A1234AA ",
    ],
    delimiter = '|',
  )
  fun `can handle large html files`(
    serviceName: String,
    serviceLabel: String,
    templateVersion: String,
    reportGenerationDate: String,
    subjectFirstName: String,
    subjectLastName: String,
    nomisId: String? = null,
  ) {
    val serviceConfig = setupServiceConfigurationWithTemplateVersion(serviceName, serviceLabel, templateVersion)
    val requestDate = LocalDate.parse(reportGenerationDate, DateTimeFormatter.ISO_DATE)

    val params = setupTestData(
      serviceConfig = serviceConfig,
      templateVersion = templateVersion,
      reportGenerationDate = requestDate,
      subjectFirstName = subjectFirstName,
      subjectLastName = subjectLastName,
      nomisId = nomisId,
      ndeliusId = null,
    )

    runBlocking {
      sarProcessor.execute()
//      await()
//        .atMost(10, TimeUnit.SECONDS)
//        .until { requestHasStatus(params.sar(), Status.Completed) }
    }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, params.htmlRenderRequest())
    documentApi.verifyStoreDocumentIsCalled(1, params.sar().id.toString())

    Files.copy(
      getUploadedPdfDocumentAsFile(tempDir.resolve("${serviceConfig.serviceName}.pdf")).toPath(),
      PdfTestUtil.getResourcePath("/integration-tests/regression").resolve("$serviceName-test-output-incremental.pdf"),
    )
  }

  //@Disabled
  @Test
  fun `produce html`() {
    val outputFile = PdfTestUtil.getResourcePath("/integration-tests/regression").resolve("wiremock-test-service-A1234AA.html")

    val styleFile = PdfTestUtil.getResourcePath("/integration-tests/regression/service-html/").resolve("style.html")
    val style = styleFile.toFile().readText()

    outputFile
      .toFile()
      .bufferedWriter()
      .use { writer ->
        writer.append(PdfTestUtil.getResourceAsString("/integration-tests/regression/service-html/style.html"))
        writer.newLine()
        writer.write("""<html><head><meta charset="UTF-8">$style</head><body>""".trimIndent())

        repeat(10000) { i ->
          writer.newLine()
          writer.append("""
          <table class="summary-list">
          <tr><td>Attachment reference</td><td>Attachment $i</td></tr>
          <tr><td>Filename</td><td>map.jpg</td></tr>
          <tr><td>File type</td><td>image/jpeg</td></tr>
          <tr><td>File size</td><td>27566</td></tr>
          <tr><td>Description</td><td>Map image</td></tr>
          <tr><td>Image $i,1</td><td><img src="http://localhost:8090/download/$i/map.jpg" alt="Inline attachment" height="400" width="null"></td></tr>
          <tr><td>Image $i.2</td><td><img src="http://localhost:8090/download/$i/image.png" alt="Inline attachment" height="400" width="null"></td></tr>

          </table>
          """.trimIndent()
          )
          writer.newLine()
        }
        writer.flush()
        writer.write("</body></html>")
      }

    println("file:///$outputFile")
  }

  internal fun setupTestData(
    serviceConfig: ServiceConfiguration,
    templateVersion: String,
    reportGenerationDate: LocalDate,
    subjectFirstName: String,
    subjectLastName: String,
    nomisId: String? = null,
    ndeliusId: String? = null,
  ): Pair<SubjectAccessRequest, HtmlRendererApiClient.HtmlRenderRequest> {
    val sar = createSubjectAccessRequestRecord(nomisId, ndeliusId, serviceConfig, reportGenerationDate)

    hmppsAuth.stubGrantToken()

    doAnswer { invocation ->
      val date = invocation.arguments[0] as LocalDate
      date.format(reportGenerationDateFormat)
    }.`when`(dateService).reportDateFormat(anyOrNull())

    doAnswer { invocation ->
      val date = invocation.arguments[0] as LocalDate?
      date?.format(reportGenerationDateFormat) ?: "Start of record"
    }.`when`(dateService).reportDateFormat(anyOrNull(), eq("Start of record"))

    whenever(dateService.reportGenerationDate())
      .thenReturn(reportGenerationDate.format(reportGenerationDateFormat))

    val htmlRenderRequest = HtmlRendererApiClient.HtmlRenderRequest(
      subjectAccessRequest = sar,
      serviceConfigurationId = serviceConfig.id,
    )

    htmlRendererSuccessfullyRendersHtml(
      sar = sar,
      htmlRenderRequest = htmlRenderRequest,
      serviceName = serviceConfig.serviceName,
      fileToAddToBucket = "/integration-tests/regression/service-html/${serviceConfig.serviceName}-${nomisId ?: ndeliusId}.html",
      templateVersion = templateVersion,
    )

    sar.nomisId?.let {
      prisonApi.stubGetOffenderDetails(it, subjectFirstName, subjectLastName)
    }
    sar.ndeliusCaseReferenceId?.let {
      probationApi.stubGetOffenderDetails(sar.ndeliusCaseReferenceId!!, subjectFirstName, subjectLastName)
    }

    documentApi.stubUploadFileSuccess(sar)
    return Pair(sar, htmlRenderRequest)
  }

  private fun createSubjectAccessRequestRecord(
    nomisId: String?,
    ndeliusId: String?,
    serviceConfig: ServiceConfiguration,
    dateTo: LocalDate,
  ): SubjectAccessRequest {
    val sar = SubjectAccessRequest(
      id = testSubjectAccessRequestId,
      dateFrom = null,
      dateTo = dateTo,
      sarCaseReferenceNumber = "DL",
      services = mutableListOf(),
      nomisId = nomisId,
      ndeliusCaseReferenceId = ndeliusId,
      requestedBy = "DL",
      status = Status.Pending,
    )
    sar.services.add(
      RequestServiceDetail(
        subjectAccessRequest = sar,
        serviceConfiguration = serviceConfig,
        renderStatus = RenderStatus.PENDING,
      ),
    )
    return subjectAccessRequestRepository.saveAndFlush(sar)
  }

  private fun setupServiceConfigurationWithTemplateVersion(
    serviceName: String,
    serviceLabel: String,
    templateVersion: String,
  ): ServiceConfiguration = serviceConfigurationRepository.save(
    createTestServiceConfiguration(
      name = serviceName,
      label = serviceLabel,
    ),
  ).let {
    templateVersionRepository.save(createTemplateVersion(it, version = templateVersion.toInt()))
    it
  }

  internal fun Pair<SubjectAccessRequest, HtmlRendererApiClient.HtmlRenderRequest>.sar() = this.first

  internal fun Pair<SubjectAccessRequest, HtmlRendererApiClient.HtmlRenderRequest>.htmlRenderRequest() = this.second
}
