package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.attachments

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.NoSchedulingConfig
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.S3TestUtils
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.PdfService

@TestPropertySource(
  properties = [
    "html-renderer.enabled=true",
  ],
)
@Import(S3TestUtils::class, NoSchedulingConfig::class)
abstract class BasePdfRendererIntTest : IntegrationTestBase() {

  companion object {
    protected const val SERVICE_NAME = "create-and-vary-a-licence-api"
  }

  @Autowired
  protected lateinit var s3TestUtil: S3TestUtils

  @Autowired
  protected lateinit var pdfService: PdfService

  @AfterEach
  fun cleanup() {
    s3TestUtil.clearBucket()
  }

  protected fun storeEmptyHtml(sar: SubjectAccessRequest) = runBlocking {
    val documentKey = "${sar.id}/$SERVICE_NAME.html"
    s3TestUtil.putFile(S3TestUtils.S3File(documentKey, "EMPTY"))
    assertThat(s3TestUtil.documentExists(documentKey)).isTrue()
  }

  protected fun storeAttachment(sar: SubjectAccessRequest, filename: String, contentType: String) = runBlocking {
    val documentKey = "${sar.id}/$SERVICE_NAME/attachments/$filename"
    val content = getAttachmentBytes(filename)
    s3TestUtil.putFile(
      S3TestUtils.S3AttachmentFile(
        key = documentKey,
        content = content,
        contentType = contentType,
        contentLength = content.size.toLong(),
        filename = filename,
        attachmentNumber = 1,
        name = "Test attachment file $filename",
      ),
    )
    assertThat(s3TestUtil.documentExists(documentKey)).isTrue()
  }

  protected fun getAttachmentBytes(filename: String): ByteArray = this::class.java
    .getResourceAsStream("/integration-tests/attachments/$filename").use { it?.readAllBytes()!! }
}
