package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path

/**
 * PdfRenderRequest create temp directory for the PDF files to be generated in. Caller is responsible for calling close
 * to ensure files are clean up once processing is complete.
 */
class PdfRenderRequest(
  val subjectAccessRequest: SubjectAccessRequest,
  val subjectName: String,
  val reportDir: Path,
): Closeable {
  val pdfPartialsDir: Path = this.reportDir.resolve("partials")
  val htmlPartialsDir: Path = this.reportDir.resolve("html")

  init {
    if (!Files.exists(pdfPartialsDir)) {
      Files.createDirectories(pdfPartialsDir)
    }
    if (!Files.exists(htmlPartialsDir)) {
      Files.createDirectories(htmlPartialsDir)
    }
  }

  fun resolvePartialPdfPath(filename: String): Path = pdfPartialsDir.resolve("$filename.pdf")

  fun resolveServiceHtmlPath(serviceName: String): Path = htmlPartialsDir.resolve("$serviceName.html")

  fun getFullPdfPath(): Path = reportDir.resolve("report.pdf")

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PdfRenderRequest

    if (subjectAccessRequest != other.subjectAccessRequest) return false
    if (subjectName != other.subjectName) return false
    if (reportDir != other.reportDir) return false

    return true
  }

  override fun hashCode(): Int {
    var result = subjectAccessRequest.hashCode()
    result = 31 * result + subjectName.hashCode()
    result = 31 * result + reportDir.hashCode()
    return result
  }

  override fun close() {
    if(Files.exists(reportDir)) {
      reportDir.toFile().deleteRecursively()
    }
  }
}