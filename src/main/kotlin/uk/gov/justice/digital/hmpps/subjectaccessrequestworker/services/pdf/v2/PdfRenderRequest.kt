package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Encapsulates details required to create PDF report document and manages the creation of a temporary working directory
 * structure used in the PDF generation. Caller must invoke close when processing is completed to ensure temporary
 * directories and files are removed.
 */
class PdfRenderRequest(
  val subjectAccessRequest: SubjectAccessRequest,
  val subjectName: String,
  val reportDir: Path,
) : Closeable {

  private companion object {
    private val log = LoggerFactory.getLogger(PdfRenderRequest::class.java)
    private const val INTERNAL_COVER_PAGE = "internalCoverPage.pdf"
    private const val REPORT_BODY = "reportBody.pdf"
    private const val INTERNAL_CONTENTS_PAGE = "internalContentsPage.pdf"
    private const val EXTERNAL_COVER_PAGE = "externalCoverPage.pdf"
    private const val REAR_PAGE = "rearPage.pdf"
    private const val REPORT_PDF = "report.pdf"
  }

  private val pdfPartialsDir: Path = reportDir.resolve("partials")
  private val htmlPartialsDir: Path = reportDir.resolve("html")

  /**
   * Path to the Internal Cover PDF page.
   */
  val internalCoverPagePdfPath: Path = pdfPartialsDir.resolve(INTERNAL_COVER_PAGE)

  /**
   * Path to the Internal Contents PDF page.
   */
  val internalContentsPagePdfPath: Path = pdfPartialsDir.resolve(INTERNAL_CONTENTS_PAGE)

  /**
   * Path to the External Cover PDF page.
   */
  val externalCoverPagePdfPath: Path = pdfPartialsDir.resolve(EXTERNAL_COVER_PAGE)

  /**
   * Path to the report body PDF. The report body is a PDF containing the service data and service
   * attachment pages merged into a single document.
   */
  val reportBodyPdfPath: Path = pdfPartialsDir.resolve(REPORT_BODY)

  /**
   * Path to the Rear PDF page.
   */
  val rearPagePdfPath: Path = pdfPartialsDir.resolve(REAR_PAGE)

  /**
   * Path to the full subject access request report document.
   */
  val fullReportPdfPath: Path = reportDir.resolve(REPORT_PDF)

  init {
    Files.createDirectories(pdfPartialsDir)
    Files.createDirectories(htmlPartialsDir)
  }

  fun serviceDataPdfPath(
    service: ServiceConfiguration,
  ): Path = pdfPartialsDir.resolve("${service.serviceName}.pdf")

  fun serviceHtmlPath(
    service: ServiceConfiguration,
  ): Path = htmlPartialsDir.resolve("${service.serviceName}.html")

  fun serviceAttachmentsPdfPath(
    service: ServiceConfiguration,
  ): Path = pdfPartialsDir.resolve("${service.serviceName}-attachments.pdf")

  override fun close() {
    log.info("cleaning up PdfRenderRequest temporary working directory {}", reportDir)

    if (!reportDir.toFile().deleteRecursively()) {
      log.warn("failed to recursively delete PdfRenderRequest temporary working directory {}", reportDir)
    }
  }

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
}
