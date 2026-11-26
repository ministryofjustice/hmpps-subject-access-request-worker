package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient.HtmlRenderRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestFixture.Companion.testSubjectAccessRequestId
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
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

@Import(S3TestUtils::class)
@TestPropertySource(properties = ["application.service-renderer=openhtmltopdf"])
class SubjectAccessRequestProcessorOpenHtmlIntTest : SubjectAccessRequestProcessorIntTestBase() {
  override val referencePdfBaseDir: String = "$REFERENCE_PDF_BASE_DIR/openhtmltopdf"

  @Autowired
  private lateinit var s3TestUtils: S3TestUtils

  @TempDir
  lateinit var tempDir: Path

  private lateinit var serviceConfig: ServiceConfiguration
  private lateinit var templateVersion: TemplateVersion

  override fun customSetup() {
    // No custom setup needed
    s3TestUtils.clearBucket()
  }

  @Test
  fun `should render embedded images`() = runBlocking {
    serviceConfig = createServiceConfiguration(
      serviceName = "mock-service-openhtmltopdf-embedded-inline-attachments",
      label = "Wiremock Test Service",
    )

    templateVersion = createTemplateVersion(
      serviceConfiguration = serviceConfig,
      fileHash = "cebd3e5ba1d6da7fdda3a20a0eaea1e8886df69b6c95db5b0c877730ec87842f",
      version = 13,
    )
    whenever(dateService.reportGenerationDate())
      .thenReturn("25 November 2026")

    val sar = SubjectAccessRequest(
      id = testSubjectAccessRequestId,
      dateFrom = null,
      dateTo = LocalDate.of(2026, 11, 25),
      sarCaseReferenceNumber = "DL",
      services = mutableListOf(),
      nomisId = "A1234AA",
      ndeliusCaseReferenceId = null,
      requestedBy = "Me",
      status = Status.Pending,
      requestDateTime = LocalDateTime.of(2026, 11, 25, 12, 0, 0),
    )
    sar.services.add(
      RequestServiceDetail(
        subjectAccessRequest = sar,
        serviceConfiguration = serviceConfig,
        renderStatus = RenderStatus.PENDING,
        templateVersion = templateVersion,
      ),
    )
    subjectAccessRequestRepository.save(sar)

    val htmlRenderRequest = HtmlRenderRequest(
      subjectAccessRequest = sar,
      serviceConfigurationId = serviceConfig.id,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererSuccessfullyRendersHtml(
      sar,
      htmlRenderRequest,
      serviceConfig.serviceName,
      "13",
      metadata = mapOf("template_version" to "v13"),
    )
    prisonApi.stubGetOffenderDetails(sar.nomisId!!)
    documentApi.stubUploadFileSuccess(sar)

    sarProcessor.execute()

    hmppsAuth.verifyCalledOnce()
    htmlRendererApi.verifyRenderCalled(1, htmlRenderRequest)
    documentApi.verifyStoreDocumentIsCalled(1, sar.id.toString())

    assertUploadedDocumentMatchesExpectedPdf(serviceConfig.serviceName)
    assertSubjectAccessRequestHasStatus(sar, Completed)
    assertSubjectAccessRequestServiceHasStatusAndTemplateVersion(
      subjectAccessRequest = sar,
      serviceName = serviceConfig.serviceName,
      status = RenderStatus.COMPLETE,
      version = 13,
    )
  }

  @Test
  fun `should render images with custom sar-inline-attachments url host`() = runTest {
    serviceConfig = createServiceConfiguration(
      serviceName = "mock-service-openhtmltopdf-inline-attachments",
      label = "Wiremock Test Service",
    )

    templateVersion = createTemplateVersion(
      serviceConfiguration = serviceConfig,
      fileHash = "cebd3e5ba1d6da7fdda3a20a0eaea1e8886df69b6c95db5b0c877730ec87842f",
      version = 13,
    )

    addInlineAttachmentToBucket(
      filename = "map.jpg",
      attachmentFilename = "35eab234-d32f-4008-9ad7-9af5aef7e985.jpg",
    )

    addInlineAttachmentToBucket(
      filename = "image.png",
      attachmentFilename = "d3e0ace5-3bd5-4f88-bf33-4343c15fd89e.png",
    )

    addInlineAttachmentToBucket(
      filename = "map.gif",
      attachmentFilename = "d856863c-067a-4489-adaf-1463b40a3e89.gif",
      contentType = "image/gif",
    )

    addAttachmentToBucket(
      filename = "map.jpg",
      attachmentFilename = "Map image",
      contentType = "image/jpeg",
      attachmentNumber = 1,
    )

    addAttachmentToBucket(
      filename = "doc1.pdf",
      attachmentFilename = "Test document",
      contentType = "application/pdf",
      attachmentNumber = 2,
    )

    addAttachmentToBucket(
      filename = "test.mp4",
      attachmentFilename = "Video recording",
      contentType = "video/mp4",
      attachmentNumber = 3,
    )

    addAttachmentToBucket(
      filename = "doc1.docx",
      attachmentFilename = "Word document",
      contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      attachmentNumber = 4,
    )

    whenever(dateService.reportGenerationDate())
      .thenReturn("26 November 2026")

    val sar = SubjectAccessRequest(
      id = testSubjectAccessRequestId,
      dateFrom = null,
      dateTo = LocalDate.of(2026, 11, 26),
      sarCaseReferenceNumber = "Test1",
      services = mutableListOf(),
      nomisId = "A1234AA",
      ndeliusCaseReferenceId = null,
      requestedBy = "Me",
      status = Status.Pending,
      requestDateTime = LocalDateTime.of(2026, 11, 25, 12, 0, 0),
    )
    sar.services.add(
      RequestServiceDetail(
        subjectAccessRequest = sar,
        serviceConfiguration = serviceConfig,
        renderStatus = RenderStatus.PENDING,
        templateVersion = templateVersion,
      ),
    )
    subjectAccessRequestRepository.save(sar)

    val htmlRenderRequest = HtmlRenderRequest(
      subjectAccessRequest = sar,
      serviceConfigurationId = serviceConfig.id,
    )

    hmppsAuth.stubGrantToken()
    htmlRendererSuccessfullyRendersHtml(
      sar,
      htmlRenderRequest,
      serviceConfig.serviceName,
      "13",
      metadata = mapOf("template_version" to "v13"),
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
      status = RenderStatus.COMPLETE,
      version = 13,
    )
  }

  private fun addInlineAttachmentToBucket(filename: String, attachmentFilename: String, contentType: String = "image/jpeg") {
    val image2Bytes = getAttachmentBytes(filename)
    val key = "$testSubjectAccessRequestId/mock-service-openhtmltopdf-inline-attachments/inline-attachments/$attachmentFilename"
    s3TestUtils.putFile(
      S3TestUtils.S3AttachmentFile(
        key = key,
        content = image2Bytes,
        contentType = contentType,
        contentLength = image2Bytes.size.toLong(),
        attachmentNumber = 1,
        name = filename,
        filename = filename,
      ),
    )
  }

  private fun addAttachmentToBucket(filename: String, attachmentFilename: String, contentType: String, attachmentNumber: Int) {
    val contentBytes = getAttachmentBytes(filename)
    s3TestUtils.putFile(S3TestUtils.S3AttachmentFile(
      key = "$testSubjectAccessRequestId/mock-service-openhtmltopdf-inline-attachments/attachments/$attachmentNumber-$filename",
      content = contentBytes,
      contentType = contentType,
      contentLength = contentBytes.size.toLong(),
      attachmentNumber = attachmentNumber,
      name = attachmentFilename,
      filename = filename,
    ))
  }
}
