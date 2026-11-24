package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.styledxmlparser.resolver.resource.DefaultResourceRetriever
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.DocumentStoreService
import java.io.InputStream
import java.net.URL

@Service
class SarResourceRetriever(
  val documentStoreService: DocumentStoreService,
) : DefaultResourceRetriever() {

  companion object {
    private val log = LoggerFactory.getLogger(SarResourceRetriever::class.java)
  }

  override fun getInputStreamByUrl(url: URL?): InputStream? {
    if (url?.host == "sar-inline-attachments") {
      log.info("resolving sar-inline-attachment image url: {}", url.toString())
      return getAttachmentFromDocumentStore(url)
    }

    log.info("resolving non sar-inline-attachment image url: {}", url.toString())
    return super.getInputStreamByUrl(url)
  }

  private fun getAttachmentFromDocumentStore(url: URL): InputStream = runBlocking {
    log.info("get attachment from store blocking: {}", url.path)
    try {
      documentStoreService.getInlineAttachment(url.inlineAttachmentDocumentKey())
    } catch (ex: Exception) {
      log.error("error getting attachment from store: {}", url.toString(), ex)
      throw ex
    }
  }

  private fun URL.inlineAttachmentDocumentKey(): String = this.path.removePrefix("/")

  override fun getByteArrayByUrl(url: URL?): ByteArray? = super.getByteArrayByUrl(url)
}
