package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental

import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.TempDirectoryService
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.toPath

class TempFileImageStore(
  val tempDirectoryService: TempDirectoryService,
  val subjectAccessRequest: SubjectAccessRequest,
) : ImageStore {

  private companion object {
    private val log = LoggerFactory.getLogger(TempFileImageStore::class.java)
  }

  private val imagesDir = tempDirectoryService
    .create("${subjectAccessRequest.id}_${System.currentTimeMillis()}_images")
    .createDirectories()

  override fun add(embeddedImage: EmbeddedImage): URI {
    val imagePath = imagesDir.resolve(embeddedImage.getFilename(UUID.randomUUID())).toUri()
    log.info("adding SAR PDF image to image store: {}", imagePath)
    Files.write(imagePath.toPath(), embeddedImage.embeddedImageToByteArray())
    return imagePath
  }

  override fun flush() {
    log.info("clearing TempFileImageStore directory store for SAR ID {}", subjectAccessRequest.id)
    imagesDir.toFile().listFiles()?.forEach {
      log.info("clearing TempFileImageStore: $it")
      it.deleteRecursively()
    }
  }

  override fun close() {
    if (imagesDir.toFile().exists()) {
      log.info("deleting TempFileImageStore directory: {}", imagesDir)
      imagesDir.toFile().deleteRecursively()
    }
  }
}
