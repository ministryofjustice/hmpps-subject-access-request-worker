package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration

import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.Status.Completed
import java.util.concurrent.TimeUnit

@Import(S3TestUtils::class)
@TestPropertySource(properties = ["application.service-renderer=itext"])
class SubjectAccessRequestProcessorITextIntTest : SubjectAccessRequestProcessorIntTestBase() {
  override val referencePdfBaseDir: String = "$REFERENCE_PDF_BASE_DIR/itext"

  @Autowired
  private lateinit var s3TestUtils: S3TestUtils

  override fun customSetup() {
    s3TestUtils.clearBucket()
  }

  @Test
  fun `should retrieve image tags with custom inline attachments url from S3 bucket`() = runBlocking {
    val serviceConfig = createServiceConfiguration(
      serviceName = "test-service-inline-attachments",
      label = "Test Service Inline Attachments",
    )
    createTemplateVersion(
      serviceConfiguration = serviceConfig,
      fileHash = "cebd3e5ba1d6da7fdda3a20a0eaea1e8886df69b6c95db5b0c877730ec87842f",
    )

    addInlineAttachmentToBucket(
      filename = "map.jpg",
      documentKey = "$testSubjectAccessRequestId/test-service-inline-attachments/inline-attachments/35eab234-d32f-4008-9ad7-9af5aef7e985.jpg",
    )

    addInlineAttachmentToBucket(
      filename = "large.jpg",
      documentKey = "$testSubjectAccessRequestId/test-service-inline-attachments/inline-attachments/d3e0ace5-3bd5-4f88-bf33-4343c15fd89e.jpg",
    )

    val sar = insertSubjectAccessRequest(serviceConfig, Status.Pending)
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
    assertSubjectAccessRequestServiceHasStatusAndTemplateVersion(
      subjectAccessRequest = sar,
      serviceName = serviceConfig.serviceName,
      status = RenderStatus.COMPLETE,
      version = 1,
    )
  }

  private fun addInlineAttachmentToBucket(filename: String, documentKey: String) {
    val image2Bytes = getAttachmentBytes(filename)
    s3TestUtils.putFile(
      S3TestUtils.S3AttachmentFile(
        key = documentKey,
        content = image2Bytes,
        contentType = "image/jpg",
        contentLength = image2Bytes.size.toLong(),
        attachmentNumber = 1,
        name = filename,
        filename = filename,
      ),
    )
  }
}
