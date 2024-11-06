package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.google.gson.Gson
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.nimbusds.jose.util.StandardCharset
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.or
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.util.UUID

class PdfServiceTest {

  private lateinit var pdfService: GeneratePdfService

  companion object {
    const val TEST_DATA_PATH =
      "/Users/david.llewellyn/development/hmpps-subject-access-request-worker/book-a-move-test-data.json"

    const val PDF_V1 =
      "/Users/david.llewellyn/development/hmpps-subject-access-request-worker/sar-original.pdf"


    const val PDF_V2 =
      "/Users/david.llewellyn/development/hmpps-subject-access-request-worker/sar-refactored.pdf"
  }

  @BeforeEach
  fun setup() {
    this.pdfService = GeneratePdfService()
  }

  @Test
  fun generatePdf() {
    val pdfStream = pdfService.execute(
      services = listOf(DpsService(name = "hmpps-book-secure-move-api", orderPosition = 1, content = getTestData())),
      nomisId = "1234",
      ndeliusCaseReferenceId = "nDeliusRef123",
      sarCaseReferenceNumber = "sarCaseRef",
      subjectName = "Lord Voldemort",
      dateFrom = LocalDate.now().minusYears(1),
      dateTo = LocalDate.now(),
      subjectAccessRequest = SubjectAccessRequest(id = UUID.randomUUID()),
      pdfStream = ByteArrayOutputStream(),
    )

    pdfStream.use { stream ->
      println("Files bytes (human readable)? ${FileUtils.byteCountToDisplaySize(stream.size())}")
      println("Files bytes? ${stream.size()}")
      Files.write(Paths.get(PDF_V1), stream.toByteArray())
    }
  }

//  @Test
//  fun myPdfTest() {
//
//    FileOutputStream(Paths.get(OUTPUT_FILE).toFile()).use { fos ->
//      val writer = PdfWriter(fos)
//      val pdfDoc = PdfDocument(writer)
//      Document(pdfDoc).use { doc ->
//        doc.add(Paragraph().add("Hello World!"))
//      }
//    }
//  }

  @Test
  fun v2HereWeGp() {
    val services = listOf(DpsService(name = "hmpps-book-secure-move-api", orderPosition = 1, content = getTestData()))
    val pdfOutputStream = PdfServiceV2().generateSubjectAccessRequestPDF(
      services = services,
      nomisId = "1234",
      ndeliusCaseReferenceId = "nDeliusRef123",
      sarCaseReferenceNumber = "sarCaseRef",
      subjectName = "Lord Voldemort",
      dateFrom = LocalDate.now().minusYears(1),
      dateTo = LocalDate.now(),
      subjectAccessRequest = SubjectAccessRequest(id = UUID.randomUUID()),
    )

    pdfOutputStream.use { stream ->
      println("Files bytes (human readable)? ${FileUtils.byteCountToDisplaySize(stream.size())}")
      println("Files bytes? ${stream.size()}")

      Files.write(Paths.get(PDF_V2), stream.toByteArray())
    }
  }

  @Test
  fun readPdf() {
    Document(PdfDocument(PdfReader(PDF_V1))).use { original ->
      Document(PdfDocument(PdfReader(PDF_V2))).use { refactored ->
        val originalPdf = original.pdfDocument
        val refactorPdf = refactored.pdfDocument
        assertThat(originalPdf.numberOfPages).isEqualTo(refactorPdf.numberOfPages)
        for (i in 1 until originalPdf.numberOfPages) {

          val originalPage = PdfTextExtractor.getTextFromPage(originalPdf.getPage(i))
          val refactorPage = PdfTextExtractor.getTextFromPage(refactorPdf.getPage(i))

          println("Diff: ${StringUtils.difference(originalPage, refactorPage)}")

          assertThat(refactorPage)
            .withFailMessage("Page $i did not match")
            .isEqualTo(originalPage)
        }

      }
    }
  }

  fun getTestData(): Any {
    val jsonStr = Files.readString(Paths.get(TEST_DATA_PATH))
    val map: Map<*, *> = Gson().fromJson(jsonStr, Map::class.java)
    return map["content"] as Any
  }
}