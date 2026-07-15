package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient.HtmlRenderRequest
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status.Completed
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.TemplateVersionStatus.PUBLISHED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getInputStream
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.getReadablePdfDocument
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

class PdfChunkingIntTest : BaseProcessorIntTest() {

  val expectedPdfBaseDir = "/integration-tests/reference-pdfs"

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
        status = PUBLISHED,
        version = 1,
        fileHash = "12345",
      ),
    )

    doAnswer { invocation ->
      val date = invocation.arguments[0] as LocalDate
      date.format(reportGenerationDateFormat)
    }.`when`(dateService).reportDateFormat(anyOrNull())

    doAnswer { invocation ->
      val date = invocation.arguments[0] as LocalDate?
      date?.format(reportGenerationDateFormat) ?: "Start of record"
    }.`when`(dateService).reportDateFormat(anyOrNull(), eq("Start of record"))

    whenever(dateService.reportGenerationDate())
      .thenReturn(LocalDate.of(2026, 7, 9).format(reportGenerationDateFormat))
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
      "hmpps-support-additional-needs-api | Unicabhai | BEVASTIEN | G4874UQ   | ",
      "hmpps-book-secure-move-api         | Dougal    | WOW       | A1234AA   | ",
      "hmpps-uof-data-api                 | Ibaravid  | AIDIO     | G0257UO   | ",
      "make-recall-decision-api           | Bethany   | SCHINNER  |           | X963906",
      "hmpps-managing-prisoner-apps-api   | Joe       | REACHER   | nomis-666 | ",
    ],
    delimiter = '|',
  )
  fun `generates the expected PDF content`(
    serviceName: String,
    subjectFirstName: String,
    subjectLastName: String,
    nomisId: String? = null,
    ndeliusId: String? = null,
  ) = runTest {
    val serviceConfig = getServiceConfiguration(serviceName)
    assertThat(serviceConfig).isNotNull

    val params = setupTestData(serviceConfig, subjectFirstName, subjectLastName, nomisId, ndeliusId)

    sarProcessor.execute()
    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestHasStatus(params.sar(), Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, params.htmlRenderRequest())
    documentApi.verifyStoreDocumentIsCalled(1, params.sar().id.toString())

    val expectedPdfPath = resolveResourcePath(expectedPdfBaseDir).resolve("${serviceName}-reference.pdf")
    getReadablePdfDocument(getInputStream(expectedPdfPath)).use { expected ->
      getUploadedPdfDocument().use { actual ->
        assertThat(actual.numberOfPages).isEqualTo(expected.numberOfPages)

        // Ignore the first 3 pages as the report generation date and template version can vary depending on when it was generated
        for (i in 4 until expected.numberOfPages) {
          val actualText = PdfTextExtractor.getTextFromPage(actual.getPage(i), SimpleTextExtractionStrategy())
          val expectedText = PdfTextExtractor.getTextFromPage(expected.getPage(i), SimpleTextExtractionStrategy())
          assertThat(actualText).`as`("page $i text values").isEqualTo(expectedText)
        }
      }
    }
  }

  @Test
  fun `should handle uber html`() = runTest(timeout = 4.minutes) {
    val serviceConfig = getServiceConfiguration("hmpps-accredited-programmes-api")
    assertThat(serviceConfig).isNotNull

    val params = setupTestData(serviceConfig, "BOB", "BOB", "1234567", null)

    sarProcessor.execute()
    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestHasStatus(params.sar(), Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, params.htmlRenderRequest())
    documentApi.verifyStoreDocumentIsCalled(1, params.sar().id.toString())
  }

  internal fun setupTestData(
    serviceConfig: ServiceConfiguration,
    subjectFirstName: String,
    subjectLastName: String,
    nomisId: String? = null,
    ndeliusId: String? = null,
  ): Pair<SubjectAccessRequest, HtmlRenderRequest> {
    val sar = SubjectAccessRequest(
      id = testSubjectAccessRequestId,
      dateFrom = null,
      dateTo = LocalDate.of(2026, 7, 9),
      sarCaseReferenceNumber = "Bob Jones",
      services = mutableListOf(),
      nomisId = nomisId,
      ndeliusCaseReferenceId = ndeliusId,
      requestedBy = "Jennifer Yellow Hat",
      status = Status.Pending,
    )
    sar.services.add(
      RequestServiceDetail(
        subjectAccessRequest = sar,
        serviceConfiguration = serviceConfig,
        renderStatus = RenderStatus.PENDING,
      ),
    )
    subjectAccessRequestRepository.saveAndFlush(sar)

    val htmlRenderRequest = HtmlRenderRequest(
      subjectAccessRequest = sar,
      serviceConfigurationId = serviceConfig.id,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererSuccessfullyRendersHtml(sar, htmlRenderRequest, serviceConfig.serviceName, "1")

    sar.nomisId?.let {
      prisonApi.stubGetOffenderDetails2(it, subjectFirstName, subjectLastName)
    } ?: run {
      probationApi.stubGetOffenderDetails(sar.ndeliusCaseReferenceId!!, subjectFirstName, subjectLastName)
    }

    documentApi.stubUploadFileSuccess(sar)

    return Pair(sar, htmlRenderRequest)
  }

  internal fun Pair<SubjectAccessRequest, HtmlRenderRequest>.sar() = this.first

  internal fun Pair<SubjectAccessRequest, HtmlRenderRequest>.htmlRenderRequest() = this.second
}