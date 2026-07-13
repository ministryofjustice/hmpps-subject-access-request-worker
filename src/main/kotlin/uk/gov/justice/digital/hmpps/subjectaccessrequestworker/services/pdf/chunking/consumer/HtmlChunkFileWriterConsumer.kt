package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking.consumer

import java.nio.file.Files
import java.nio.file.Path

class HtmlChunkFileWriterConsumer(filepath: Path) : HtmlChunkConsumer {

  private val writer = Files.newBufferedWriter(filepath)

  override fun consume(chunk: String) {
    writer.write(chunk)
    writer.newLine()
    writer.flush()
  }

  override fun close() {
    this.writer.close()
  }
}