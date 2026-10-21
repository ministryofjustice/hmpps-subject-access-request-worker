package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.TemplateVersionStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.PdfTestUtil
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@TestPropertySource(
  properties = [
    "application.service-renderer=itext_incremental"
  ],
)
@DirtiesContext
class PdfGenerationRegressionIntTest : BaseProcessorIntTest() {

  @TempDir
  lateinit var tempDir: Path

  @MockitoBean
  private lateinit var dateService: DateService

  private val reportGenerationDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  @BeforeEach
  fun setup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()
    templateVersionRepository.deleteAll()
    templateVersionRepository.save(
      TemplateVersion(
        serviceConfiguration = getServiceConfiguration("template-migrated-service"),
        status = TemplateVersionStatus.PUBLISHED,
        version = 1,
        fileHash = "12345",
      ),
    )
  }

  @AfterEach
  fun cleanup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()
    s3TestUtil.clearBucket()
  }

  @ParameterizedTest
  @CsvSource(
    value = [
      "offender-case-notes              | 1  |  2026-10-20 | Dougal   | WOW       | A1234AA | ",
      "hmpps-book-secure-move-api       | 27 |  2026-10-20 | Dougal   | WOW       | A1234AA | ",
      "hmpps-activities-management-api  | 3  |  2026-10-21 | Andrew   | SMITH     | A8731DY | ",
      "make-recall-decision-api         | 5  |  2026-10-21 | Terrence | GREENHOLT |         | X788206 ",
    ],
    delimiter = '|',
  )
  fun `should generate a PDF visually equal to the expected reference PDF`(
    serviceName: String,
    templateVersion: String,
    reportGenerationDate: String,
    subjectFirstName: String,
    subjectLastName: String,
    nomisId: String? = null,
    ndeliusId: String? = null,
  ) = runTest {
    val serviceConfig = getServiceConfiguration(serviceName)
    assertThat(serviceConfig).isNotNull

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

    PdfTestUtil.assertPdfsVisuallyEquivalent(
      actual = generatedPdf.toPath(),
      expected = referencePdfPath,
    )
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
      templateVersion = templateVersion
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

  internal fun Pair<SubjectAccessRequest, HtmlRendererApiClient.HtmlRenderRequest>.sar() = this.first

  internal fun Pair<SubjectAccessRequest, HtmlRendererApiClient.HtmlRenderRequest>.htmlRenderRequest() = this.second

}