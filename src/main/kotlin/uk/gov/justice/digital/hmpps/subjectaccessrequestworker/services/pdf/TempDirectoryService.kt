package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path

@Service
class TempDirectoryService(
  val subjectAccessRequestDirectory: Path = createTempDirectory("sar_"),
) {

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @PreDestroy
  fun destroy() {
    cleanUp()
  }

  internal fun cleanUp() {
    if (Files.exists(subjectAccessRequestDirectory)) {
      log.info("deleting temp directory ${subjectAccessRequestDirectory.toAbsolutePath()}")
      subjectAccessRequestDirectory.toFile().deleteRecursively()
    }
  }

  fun create(prefix: String): Path = createTempDirectory(subjectAccessRequestDirectory, prefix)
}
