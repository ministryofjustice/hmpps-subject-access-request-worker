package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Testcontainers
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient.HtmlRenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status.Completed
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.scheduled.SubjectAccessRequestProcessor
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestPropertySource(
  properties = [
    "html-renderer.enabled=true",
  ],
)
@Import(S3TestUtils::class, NoSchedulingConfig::class)
@Testcontainers
class SubjectAccessRequestProcessorHtmlRendererEnabledIntTest : BaseProcessorIntTest() {

  companion object {
    const val REPORT_GENERATION_DATE = "1 January 2025"

    lateinit var gotenberg: GenericContainer<*>

    @JvmStatic
    @BeforeAll
    fun setupContainers() {
      gotenberg = GenericContainer("gotenberg/gotenberg:8.23.1").withExposedPorts(3000)
      gotenberg.start()
      System.setProperty("gotenberg-api.url", "http://${gotenberg.host}:${gotenberg.getMappedPort(3000)}")
    }
  }

  @MockitoBean
  protected lateinit var dateService: DateService

  @Autowired
  protected lateinit var s3TestUtil: S3TestUtils

  @Autowired
  private lateinit var sarProcessor: SubjectAccessRequestProcessor

  @Autowired
  protected lateinit var serviceConfigurationRepository: ServiceConfigurationRepository

  private var attachmentNumber = 1

  @BeforeEach
  fun setup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()

    /** Ensure the test generated reports have the same 'report generation date' as pre-generated reference reports */
    whenever(dateService.reportGenerationDate())
      .thenReturn(REPORT_GENERATION_DATE)
    whenever(dateService.reportDateFormat(LocalDate.of(2025, 1, 1)))
      .thenReturn("1 January 2025")
    whenever(dateService.reportDateFormat(LocalDate.of(2024, 1, 1), "Start of record"))
      .thenReturn("1 January 2024")

    attachmentNumber = 1
  }

  @AfterEach
  fun cleanup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()
    s3TestUtil.clearBucket()
  }

  @Test
  fun `should process pending request successfully when data is held and html-renderer is enabled`() = runBlocking {
    val serviceConfig = getServiceConfiguration("hmpps-book-secure-move-api")

    val sar = insertSubjectAccessRequest(serviceConfig.serviceName, Status.Pending)
    val htmlRenderRequest = HtmlRenderRequest(
      subjectAccessRequest = sar,
      serviceConfigurationId = serviceConfig.id,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererSuccessfullyRendersHtml(sar, htmlRenderRequest, serviceConfig.serviceName)
    prisonApi.stubGetOffenderDetails(sar.nomisId!!)
    documentApi.stubUploadFileSuccess(sar)

    sarProcessor.execute()
    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestHasStatus(sar, Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, htmlRenderRequest)
    documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())

    assertUploadedDocumentMatchesExpectedPdf(serviceConfig.serviceName)
    assertSubjectAccessRequestHasStatus(sar, Completed)
  }

  @Test
  fun `should process pending request successfully when no data is held and html-renderer is enabled`() = runBlocking {
    val serviceConfig = getServiceConfiguration("hmpps-book-secure-move-api")

    val sar = insertSubjectAccessRequest(serviceConfig.serviceName, Status.Pending)

    val htmlRenderRequest = HtmlRenderRequest(
      subjectAccessRequest = sar,
      serviceConfigurationId = serviceConfig.id,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererSuccessfullyRendersHtmlNoDataHeld(sar, htmlRenderRequest, serviceConfig.serviceName)
    prisonApi.stubGetOffenderDetails(sar.nomisId!!)
    documentApi.stubUploadFileSuccess(sar)

    sarProcessor.execute()
    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestHasStatus(sar, Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, htmlRenderRequest)
    documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())

    assertUploadedDocumentMatchesExpectedNoDataHeldPdf(serviceConfig.serviceName, serviceConfig.label)
    assertSubjectAccessRequestHasStatus(sar, Completed)
  }

  @Test
  fun `should process pending request successfully when attachments exist and html-renderer is enabled`() = runBlocking {
    val serviceConfig = getServiceConfiguration("create-and-vary-a-licence-api")

    val sar = insertSubjectAccessRequest(serviceConfig.serviceName, Status.Pending)

    val htmlRenderRequest = HtmlRenderRequest(
      subjectAccessRequest = sar,
      serviceConfigurationId = serviceConfig.id,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererSuccessfullyRendersHtml(sar, htmlRenderRequest, serviceConfig.serviceName)
    storeAttachment(sar, serviceConfig.serviceName, "doc.pdf", "application/pdf")
    storeAttachment(
      sar,
      serviceConfig.serviceName,
      "doc.docx",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )
    storeAttachment(sar, serviceConfig.serviceName, "map.jpg", "image/jpeg")
    storeAttachment(sar, serviceConfig.serviceName, "map.gif", "image/gif")
    storeAttachment(sar, serviceConfig.serviceName, "map.png", "image/png")
    storeAttachment(sar, serviceConfig.serviceName, "map.tif", "image/tiff")
    storeAttachment(sar, serviceConfig.serviceName, "video.mp4", "video/mp4")
    prisonApi.stubGetOffenderDetails(sar.nomisId!!)
    documentApi.stubUploadFileSuccess(sar)

    sarProcessor.execute()
    await()
      .atMost(10, TimeUnit.SECONDS)
      .until { requestHasStatus(sar, Completed) }

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, htmlRenderRequest)
    documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())

    val expected = getPreGeneratedPdfDocument("${serviceConfig.serviceName}-attachments-reference.pdf")
    val actual = getUploadedPdfDocument()
    assertUploadedDocumentMatchesExpectedPdf(actual, expected)
    assertAttachmentPageMatchesExpected(actual, expected, 23, 1)
    assertPageMatchesExpected(actual, expected, 24)
    assertPageMatchesExpected(actual, expected, 25)
    assertPageMatchesExpected(actual, expected, 26)
    assertAttachmentPageMatchesExpected(actual, expected, 27, 2)
    assertPageMatchesExpected(actual, expected, 28)
    assertPageMatchesExpected(actual, expected, 29)

    assertPageMatchesExpected(actual, expected, 31)
    assertPageMatchesExpected(actual, expected, 30)

    assertPageMatchesExpected(actual, expected, 32)
    assertAttachmentPageMatchesExpected(actual, expected, 33, 3)
    assertAttachmentPageMatchesExpected(actual, expected, 34, 4)
    assertAttachmentPageMatchesExpected(actual, expected, 35, 5)
    assertAttachmentPageMatchesExpected(actual, expected, 36, 6)
    assertAttachmentPageMatchesExpected(actual, expected, 37, 7)
    assertSubjectAccessRequestHasStatus(sar, Completed)
  }

  private fun htmlRendererSuccessfullyRendersHtml(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRenderRequest,
    serviceName: String,
  ) = stubHtmlRendererSuccess(
    sar = sar,
    htmlRenderRequest = htmlRenderRequest,
    serviceName = serviceName,
    fileToAddToBucket = serviceName,
  )

  private fun htmlRendererSuccessfullyRendersHtmlNoDataHeld(
    sar: SubjectAccessRequest,
    htmlRenderRequest: HtmlRenderRequest,
    serviceName: String,
  ) = stubHtmlRendererSuccess(
    sar = sar,
    htmlRenderRequest = htmlRenderRequest,
    serviceName = serviceName,
    fileToAddToBucket = "$serviceName-no-data",
  )

  private fun stubHtmlRendererSuccess(
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

  fun storeAttachment(
    sar: SubjectAccessRequest,
    serviceName: String,
    filename: String,
    contentType: String,
  ) = runBlocking {
    val documentKey = attachmentDocumentKey(sar, serviceName, filename)
    val content = getAttachmentBytes(filename)
    s3TestUtil.putFile(
      S3TestUtils.S3AttachmentFile(
        key = documentKey,
        content = content,
        contentType = contentType,
        contentLength = content.size.toLong(),
        filename = filename,
        attachmentNumber = attachmentNumber++,
        name = "Test attachment file $filename",
      ),
    )
    assertThat(s3TestUtil.documentExists(documentKey)).isTrue()
  }

  fun htmlDocumentKey(sar: SubjectAccessRequest, serviceName: String) = "${sar.id}/$serviceName.html"

  fun attachmentDocumentKey(
    sar: SubjectAccessRequest,
    serviceName: String,
    filename: String,
  ) = "${sar.id}/$serviceName/attachments/$filename"

  fun getAttachmentBytes(filename: String): ByteArray = this::class.java
    .getResourceAsStream("/integration-tests/attachments/$filename").use { it?.readAllBytes()!! }

  private fun getServiceConfiguration(serviceName: String): ServiceConfiguration {
    val serviceConfig = serviceConfigurationRepository.findByServiceName(serviceName)
    assertThat(serviceConfig).isNotNull
    return serviceConfig!!
  }
}
