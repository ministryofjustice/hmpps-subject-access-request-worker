package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RequestServiceDetail
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

  val expectedPdfPath = "/integration-tests/reference-pdfs/hmpps-support-additional-needs-api-expected.pdf"

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

  @Test
  fun `HTML Chunking experiment`() = runTest {
    val serviceConfig = getServiceConfiguration("hmpps-support-additional-needs-api")
    assertThat(serviceConfig).isNotNull

    val sar = SubjectAccessRequest(
      id = testSubjectAccessRequestId,
      dateFrom = null,
      dateTo = LocalDate.of(2026, 7, 9),
      sarCaseReferenceNumber = "Nathan Testing",
      services = mutableListOf(),
      nomisId = "G4874UQ",
      ndeliusCaseReferenceId = null,
      requestedBy = "Me",
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
    htmlRendererSuccessfullyRendersHtml(sar, htmlRenderRequest, serviceConfig.serviceName, "v2")

    prisonApi.stubGetOffenderDetails2(sar.nomisId!!, "Unicabhai", "BEVASTIEN")
    documentApi.stubUploadFileSuccess(sar)

    sarProcessor.execute()
    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestHasStatus(sar, Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, htmlRenderRequest)
    documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())

    getReadablePdfDocument(getInputStream(resolveResourcePath(expectedPdfPath))).use { expected ->
      getUploadedPdfDocument().use { actual ->
        assertThat(actual.numberOfPages).isEqualTo(expected.numberOfPages)
        for (i in 1 until expected.numberOfPages) {
          assertPageMatchesExpected(actual, expected, i)
        }
      }
    }
  }

  @Test
  fun `chunk a big file`() = runTest(timeout = 3.minutes) {
    val serviceConfig = getServiceConfiguration("hmpps-accredited-programmes-api")
    assertThat(serviceConfig).isNotNull

    val sar = SubjectAccessRequest(
      id = testSubjectAccessRequestId,
      dateFrom = null,
      dateTo = LocalDate.of(2026, 7, 9),
      sarCaseReferenceNumber = "Bob Dole",
      services = mutableListOf(),
      nomisId = "A8610DY",
      ndeliusCaseReferenceId = null,
      requestedBy = "Me",
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
    htmlRendererSuccessfullyRendersHtml(sar, htmlRenderRequest, serviceConfig.serviceName, "v2")

    prisonApi.stubGetOffenderDetails2(sar.nomisId!!,"Unicabhai", "BEVASTIEN")
    documentApi.stubUploadFileSuccess(sar)

    sarProcessor.execute()
    await()
      .atMost(300, TimeUnit.SECONDS)
      .until { requestHasStatus(sar, Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, htmlRenderRequest)
    documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())
  }
}