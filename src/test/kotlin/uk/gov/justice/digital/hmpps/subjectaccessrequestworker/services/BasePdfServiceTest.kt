package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.regex.Pattern

typealias PDFContentAssertion = (pdfDocument: PdfDocument) -> Unit

abstract class BasePdfServiceTest {

  protected lateinit var pdfService: GeneratePdfService

  protected val subjectName = "Lord Voldemort"
  protected val nomisId = UUID.randomUUID().toString()
  protected val ndeliusCaseRefId = UUID.randomUUID().toString()
  protected val sarCaseRefId = UUID.randomUUID().toString()
  protected val startDate = LocalDate.now().minusYears(1)
  protected val endDate = LocalDate.now()
  protected val sarDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  protected val subjectAccessRequestId = UUID.randomUUID()

  fun LocalDate.sarFormat(): String {
    return sarDateFormatter.format(this)
  }

  @BeforeEach
  fun setup() {
    this.pdfService = GeneratePdfService()
    customSetup()
  }

  /**
   * Override for to register custom setup
   */
  open protected fun customSetup() {}

  fun writeIt(pdfStream: ByteArrayOutputStream) {
    Files.write(Paths.get("/Users/david.llewellyn/development/test4.pdf"), pdfStream.toByteArray())
  }

  protected fun generatorPdfForServices(services: List<DpsService>): ByteArrayOutputStream {
    return pdfService.execute(
      services = services,
      nomisId = nomisId,
      ndeliusCaseReferenceId = ndeliusCaseRefId,
      sarCaseReferenceNumber = sarCaseRefId,
      subjectName = subjectName,
      dateFrom = startDate,
      dateTo = endDate,
      subjectAccessRequest = SubjectAccessRequest(id = subjectAccessRequestId),
    )
  }

  protected fun assertPdfContainsExpectedContent(
    pdfOutputStream: ByteArrayOutputStream,
    assertPdfContent: PDFContentAssertion,
  ) {
    pdfOutputStream.use { pdfStream ->
      ByteArrayInputStream(pdfStream.toByteArray()).use { bais ->
        PdfReader(bais).use { pdfReader ->
          PdfDocument(pdfReader).use { pdfDocument ->
            assertPdfContent(pdfDocument)
          }
        }
      }
    }
  }

  protected fun assertPageOneContainsExpectedContent(pdfDocument: PdfDocument, expectedServices: List<String>) {
    val page1 = pdfDocument.getPageText(1)
    page1.assertContainsTextOnce("SUBJECT ACCESS REQUEST REPORT")
    page1.assertContainsTextOnce("Name: $subjectName")
    page1.assertContainsTextOnce("NOMIS ID: $nomisId")
    page1.assertContainsTextOnce("SAR Case Reference Number: $sarCaseRefId")
    page1.assertContainsTextOnce("Report date range: ${startDate.sarFormat()} - ${endDate.sarFormat()}")
    page1.assertContainsTextOnce("Report generation date: ${LocalDate.now().sarFormat()}")
    page1.assertContainsTextOnce("Services: ${expectedServices.joinToString("\n")}")
  }

  fun PdfDocument.getPageText(pageNumber: Int): PageText =
    PageText(getTextFromPage(this.getPage(pageNumber)), pageNumber)

  data class PageText(val text: String, val pageNumber: Int) {

    fun assertContainsPattern(pattern: String) {
      assertThat(this.text)
        .withFailMessage("Page $pageNumber: expected content matching pattern \"$pattern\" but no match found")
        .containsPattern(Pattern.compile(pattern))
    }

    fun assertContainsTextOnce(value: String) {
      assertThat(this.text)
        .withFailMessage("Page $pageNumber: expected \"$value\" but no match found")
        .containsOnlyOnce(value)
    }

    fun assertDoesNotContainsText(value: String) {
      assertThat(this.text)
        .withFailMessage("Page $pageNumber: expected \"$value\" but no match found")
        .doesNotContain(value)
    }
  }

}
