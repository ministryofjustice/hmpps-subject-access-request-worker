package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.openhtmltopdf.extend.FSStream
import com.openhtmltopdf.extend.FSStreamFactory
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.net.URI
import java.net.URL

class S3fsStreamFactory(
  private val delegate: FSStreamFactory,
  private val documentStoreService: DocumentStoreService,
) : FSStreamFactory {

  companion object {
    private val log = LoggerFactory.getLogger(S3fsStreamFactory::class.java)
  }

  override fun getUrl(urlStr: String?): FSStream? {
    if (urlStr == null) {
      return null
    }
    val url = URI(urlStr).toURL()
    if (url.host == "sar-inline-attachments") {
      log.info("retrieving FSStream SAR S3 implementation, url={}", url)
      val attachmentInputStream = retrieveInlineAttachmentFromDocumentStore(url)

      return object : FSStream {
        override fun getStream(): InputStream? = attachmentInputStream
        override fun getReader(): Reader? = InputStreamReader(attachmentInputStream)
      }
    }

    log.info("retrieving FSStream using default implementation, url={}", url)
    return delegate.getUrl(urlStr)
  }

  private fun retrieveInlineAttachmentFromDocumentStore(url: URL): ByteArrayInputStream = runBlocking {
    try {
      documentStoreService.getInlineAttachment(url.path.removePrefix("/"))
    } catch (ex: Exception) {
      log.error("error retrieving sar inline attachment from document store url={}", url, ex)
      throw ex
    }
  }
}
