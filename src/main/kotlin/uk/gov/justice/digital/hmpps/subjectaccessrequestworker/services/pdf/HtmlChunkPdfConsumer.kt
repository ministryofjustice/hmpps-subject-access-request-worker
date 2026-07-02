package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileWriter

class HtmlChunkPdfConsumer : HtmlChunkConsumer, Closeable {
  private val output =
    File("/Users/david.llewellyn/development/hmpps-subject-access-request-worker/src/test/resources/pdfTest/output/chunk.html")

  private val writer = BufferedWriter(FileWriter(output))

  override fun consume(chunk: String) {
    writer.write(chunk)
    writer.newLine()
    writer.flush()
  }

  override fun close() {
    this.writer.close()
  }
}