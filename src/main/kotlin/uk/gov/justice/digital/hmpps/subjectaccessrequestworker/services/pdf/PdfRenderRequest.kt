package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

data class PdfRenderRequest(
  val subjectAccessRequest: SubjectAccessRequest,
  val subjectName: String,
  val reportDir: Path = createTempDirectory("${subjectAccessRequest.id}_"),
) {
  val pdfPartialsDir: Path = this.reportDir.resolve("partials")
  val htmlPartialsDir: Path = this.reportDir.resolve("html")

  init {
    Files.createDirectories(pdfPartialsDir)
    Files.createDirectories(htmlPartialsDir)
  }

  fun resolvePartialPdfPath(filename: String): Path = pdfPartialsDir.resolve("$filename.pdf")

  fun resolveServiceHtmlPath(serviceName: String): Path = htmlPartialsDir.resolve("$serviceName.html")

  fun getPdfFilename(): String = "${this.subjectAccessRequest.id}.pdf"

  fun getFullPdfPath(): Path = reportDir.resolve(getPdfFilename())
}