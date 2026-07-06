package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
class TempDirectoryServiceTest {

  @TempDir
  lateinit var sarBaseDir: Path

  private lateinit var tempDirectoryService: TempDirectoryService

  @BeforeEach
  internal fun setUp() {
    tempDirectoryService = TempDirectoryService(sarBaseDir)
  }

  @Test
  fun `sarBaseDir should exist`() {
    assertThat(sarBaseDir).exists()
    assertThat(sarBaseDir).isDirectory()
  }

  @Test
  fun `Should create temp directory`() {
    val path = tempDirectoryService.create("sa123")
    assertThat(path).exists()
    assertThat(path).isDirectory()
  }

  @Test
  fun `should delete temp directory and all sub files and dirs`() {
    val sarDir = tempDirectoryService.create("sa123")
    val subFile = sarDir.resolve("test1.html")
    subFile.toFile().createNewFile()
    assertThat(subFile).exists()

    val subDir = sarDir.resolve("pdf-partials")
    subDir.toFile().mkdirs()
    assertThat(subDir).exists()

    tempDirectoryService.cleanUp()

    assertThat(subFile).doesNotExist()
    assertThat(subDir).doesNotExist()
    assertThat(sarBaseDir).doesNotExist()
  }
}
