package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.inlineAttachments

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.GenericContainer
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient.HtmlRenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.BaseProcessorIntTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testSubjectAccessRequestId
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.S3TestUtils
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.SubjectAccessRequestProcessorIntTestBase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.DocumentApiExtension.Companion.documentApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.HtmlRendererApiExtension.Companion.htmlRendererApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RequestServiceDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status.Completed
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.TemplateVersionStatus.PUBLISHED
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DateService
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Import(S3TestUtils::class)
abstract class BaseProcessorInlineAttachmentsIntTest : BaseProcessorIntTest() {

  companion object {
    lateinit var gotenberg: GenericContainer<*>

    @JvmStatic
    @BeforeAll
    fun setupContainers() {
      gotenberg = GenericContainer("gotenberg/gotenberg:8.23.1").withExposedPorts(3000)
      gotenberg.start()
      System.setProperty("gotenberg-api.url", "http://${gotenberg.host}:${gotenberg.getMappedPort(3000)}")
    }
  }

  @Autowired
  private lateinit var s3TestUtils: S3TestUtils

  @TempDir
  lateinit var tempDir: Path

  @MockitoBean
  protected lateinit var dateService: DateService

  @BeforeEach
  fun setup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()
    templateVersionRepository.deleteAll()
    serviceConfigurationRepository.deleteAll()

    createTemplateVersion(
      createServiceConfiguration("template-migrated-service", "Migrated Service One"),
      status = PUBLISHED,
      version = 1,
      fileHash = "12345",
    )

    /** Ensure the test generated reports have the same 'report generation date' as pre-generated reference reports */
    whenever(dateService.reportGenerationDate())
      .thenReturn(SubjectAccessRequestProcessorIntTestBase.REPORT_GENERATION_DATE)

    doAnswer { invocation ->
      val input = invocation.arguments[0] as LocalDate?
      input?.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) ?: "Start of record"
    }.whenever(dateService).reportDateFormat(anyOrNull())

    doAnswer { invocation ->
      val input = invocation.arguments[0] as LocalDate?
      input?.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) ?: invocation.arguments[1] as String
    }.whenever(dateService).reportDateFormat(anyOrNull(), eq("Start of record"))
  }

  @AfterEach
  fun cleanup() {
    clearOauthClientCache("sar-client", "anonymousUser")
    clearOauthClientCache("hmpps-subject-access-request", "anonymousUser")
    subjectAccessRequestRepository.deleteAll()
    templateVersionRepository.deleteAll()
    serviceConfigurationService.deleteAll()
    s3TestUtil.clearBucket()
  }

  protected fun createSarWithServiceDetails(
    serviceConfiguration: ServiceConfiguration,
    templateVersion: TemplateVersion? = null,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    nomisId: String,
    sarCaseReferenceNumber: String,
    requestDateTime: LocalDateTime,
  ): SubjectAccessRequest {
    val sar = SubjectAccessRequest(
      id = testSubjectAccessRequestId,
      dateFrom = dateFrom,
      dateTo = dateTo,
      sarCaseReferenceNumber = sarCaseReferenceNumber,
      services = mutableListOf(),
      nomisId = nomisId,
      ndeliusCaseReferenceId = null,
      requestedBy = "Me",
      status = Status.Pending,
      requestDateTime = requestDateTime,
    )
    sar.services.add(
      RequestServiceDetail(
        subjectAccessRequest = sar,
        serviceConfiguration = serviceConfiguration,
        renderStatus = RenderStatus.PENDING,
        templateVersion = templateVersion,
      ),
    )
    return subjectAccessRequestRepository.save(sar)
  }

  protected fun executeTest(
    serviceConfig: ServiceConfiguration,
    sar: SubjectAccessRequest,
    templateVersion: Int?,
  ) = runTest {
    val htmlRenderRequest = HtmlRenderRequest(
      subjectAccessRequest = sar,
      serviceConfigurationId = serviceConfig.id,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererSuccessfullyRendersHtml(
      sar,
      htmlRenderRequest,
      serviceConfig.serviceName,
      templateVersion.toString(),
      metadata = getTemplateVersionMetadata(templateVersion),
    )
    prisonApi.stubGetOffenderDetails(sar.nomisId!!)
    documentApi.stubUploadFileSuccess(sar)

    sarProcessor.execute()

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, htmlRenderRequest)
    documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())

    val out = getUploadedPdfDocumentAsFile(tempDir.resolve("actual.pdf"))

    assertUploadedDocumentMatchesExpectedPdf(serviceConfig.serviceName)
    assertSubjectAccessRequestHasStatus(sar, Completed)
    assertSubjectAccessRequestServiceHasStatusAndTemplateVersion(
      subjectAccessRequest = sar,
      serviceName = serviceConfig.serviceName,
      expectedStatus = RenderStatus.COMPLETE,
      expectedVersion = templateVersion,
    )
  }

  private fun getTemplateVersionMetadata(
    templateVersion: Int?,
  ): Map<String, String> = templateVersion?.let {
    mapOf("template_version" to "v$it")
  } ?: emptyMap()

  protected fun addInlineAttachmentsToS3Bucket(vararg attachments: InlineAttachment) {
    attachments.forEach {
      val image2Bytes = getAttachmentBytes(it.filename)
      val key = "$testSubjectAccessRequestId/${it.serviceConfig.serviceName}/inline-attachments/${it.attachmentFilename}"
      s3TestUtils.putFile(
        S3TestUtils.S3AttachmentFile(
          key = key,
          content = image2Bytes,
          contentType = it.contentType,
          contentLength = image2Bytes.size.toLong(),
          attachmentNumber = 1,
          name = it.filename,
          filename = it.filename,
        ),
      )
    }
  }

  protected fun addAttachmentsToS3Bucket(vararg attachments: Attachment) {
    attachments.forEach {
      val contentBytes = getAttachmentBytes(it.filename)
      s3TestUtils.putFile(
        S3TestUtils.S3AttachmentFile(
          key = "$testSubjectAccessRequestId/${it.serviceConfig.serviceName}/attachments/${it.attachmentNumber}-${it.filename}",
          content = contentBytes,
          contentType = it.contentType,
          contentLength = contentBytes.size.toLong(),
          attachmentNumber = it.attachmentNumber,
          name = it.attachmentFilename,
          filename = it.filename,
        ),
      )
    }
  }

  protected fun getAttachmentBytes(filename: String): ByteArray = this::class.java
    .getResourceAsStream("/integration-tests/attachments/$filename").use { it?.readAllBytes()!! }

  data class Attachment(
    val serviceConfig: ServiceConfiguration,
    val filename: String,
    val attachmentFilename: String,
    val contentType: String = "image/jpeg",
    val attachmentNumber: Int,
  )

  data class InlineAttachment(
    val serviceConfig: ServiceConfiguration,
    val filename: String,
    val attachmentFilename: String,
    val contentType: String = "image/jpeg",
  )
}
