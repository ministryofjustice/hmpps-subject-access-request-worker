package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status.Completed
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.testutils.TemplateTestingUtil.Companion.getFormattedReportGenerationDate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestPropertySource(
  properties = [
    "html-renderer.enabled=true",
  ],
)
@Import(S3TestUtils::class)
class SubjectAccessRequestProcessorHtmlRendererEnabledIntTest : BaseProcessorIntTest() {

  @MockitoBean
  protected lateinit var dateService: DateService

  @Autowired
  protected lateinit var s3TestUtil: S3TestUtils

  @BeforeEach
  fun setup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()

    /** Ensure the test generated reports have the same 'report generation date' as pre-generated reference reports */
    whenever(dateService.reportGenerationDate())
      .thenReturn(getFormattedReportGenerationDate())
    whenever(dateService.reportDateFormat(LocalDate.of(2025, 1, 1)))
      .thenReturn("1 January 2025")
    whenever(dateService.reportDateFormat(LocalDate.of(2024, 1, 1), "Start of record"))
      .thenReturn("1 January 2024")
  }

  @AfterEach
  fun cleanup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()
    s3TestUtil.clearBucket()
  }

  @Test
  fun `should process pending request successfully when data is held and html-renderer is enabled`() {
    val serviceName = "hmpps-book-secure-move-api"
    val serviceLabel = "Book a Secure Move"
    val sar = insertSubjectAccessRequest(serviceName, Status.Pending)
    val service = DpsService(url = "http://localhost:4100", name = serviceName, businessName = serviceLabel)
    val htmlRenderRequest = HtmlRendererApiClient.HtmlRenderRequest(
      subjectAccessRequest = sar,
      service = service,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererSuccessfullyRendersHtml(sar, htmlRenderRequest)
    prisonApi.stubGetOffenderDetails(sar.nomisId!!)
    documentApi.stubUploadFileSuccess(sar)

    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestHasStatus(sar, Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, htmlRenderRequest)
    documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())

    assertUploadedDocumentMatchesExpectedPdf(serviceName)
    assertSubjectAccessRequestHasStatus(sar, Completed)
  }

  @Test
  fun `should process pending request successfully when no data is held and html-renderer is enabled`() {
    val serviceName = "hmpps-book-secure-move-api"
    val serviceLabel = "Book a Secure Move"
    val sar = insertSubjectAccessRequest(serviceName, Status.Pending)
    val service = DpsService(url = "http://localhost:4100", name = serviceName, businessName = serviceLabel)
    val htmlRenderRequest = HtmlRendererApiClient.HtmlRenderRequest(
      subjectAccessRequest = sar,
      service = service,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererSuccessfullyRendersHtmlNoDataHeld(sar, htmlRenderRequest)
    prisonApi.stubGetOffenderDetails(sar.nomisId!!)
    documentApi.stubUploadFileSuccess(sar)

    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestHasStatus(sar, Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, htmlRenderRequest)
    documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())

    assertUploadedDocumentMatchesExpectedNoDataHeldPdf(serviceName, serviceLabel)
    assertSubjectAccessRequestHasStatus(sar, Completed)
  }

  private fun htmlRendererSuccessfullyRendersHtml(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRendererApiClient.HtmlRenderRequest,
  ) = stubHtmlRendererSuccess(sar, htmlRenderRequest, htmlRenderRequest.serviceName)

  private fun htmlRendererSuccessfullyRendersHtmlNoDataHeld(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRendererApiClient.HtmlRenderRequest,
  ) = stubHtmlRendererSuccess(
    sar = sar,
    htmlRenderRequest = htmlRenderRequest,
    fileToAddToBucket = "${htmlRenderRequest.serviceName}-no-data",
  )

  private fun stubHtmlRendererSuccess(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRendererApiClient.HtmlRenderRequest,
    fileToAddToBucket: String,
  ) = runBlocking {
    val documentKey = documentKey(sar, htmlRenderRequest.serviceName)

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

  fun documentKey(sar: SubjectAccessRequest, serviceName: String) = "${sar.id}/$serviceName.html"
}
