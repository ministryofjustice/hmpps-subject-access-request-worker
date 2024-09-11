package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import io.kotest.core.spec.style.DescribeSpec
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import java.io.FileOutputStream

@ActiveProfiles("test")
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(GeneratePdfService::class)],
)
class GeneratePdfServiceRestrictedPatientsTest(
  @Autowired val generatePdfService: GeneratePdfService,
) : DescribeSpec(
  {
    fun writeAndThenReadPdf(
      testInput: Map<String, String>?,
    ): PdfDocument {
      val testFileName = "dummy-template-restricted-patients.pdf"
      val testResponseObject = listOf(DpsService(name = "hmpps-restricted-patients-api", content = testInput))
      val mockPdfDocument = PdfDocument(PdfWriter(FileOutputStream(testFileName)))
      Document(mockPdfDocument).use {
        generatePdfService.addData(mockPdfDocument, it, testResponseObject)
      }
      return PdfDocument(PdfReader(testFileName))
    }

    describe("generatePdfService") {
      it("renders for Restricted Patients API") {
        val testInput = mapOf(
          "prisonerNumber" to "A1234AA",
          "supportingPrisonDescription" to "HMP Exeter",
          "hospitalLocationDescription" to "Weston Park Hospital",
          "dischargeTime" to "2024-09-05T08:50:44.19812",
          "commentText" to "This is a restricted patients comment",
        )
        writeAndThenReadPdf(testInput).use {
          val page = it.getPage(2)
          val text = PdfTextExtractor.getTextFromPage(page)
          assertThat(text).contains("Restricted Patients")
          assertThat(text).contains("Prison number A1234AA")
          assertThat(text).contains("Discharge time 05 September 2024, 8:50:44 am")
          assertThat(text).contains("Hospital location Weston Park Hospital")
          assertThat(text).contains("Supporting prison HMP Exeter")
          assertThat(text).contains("Comments This is a restricted patients comment")
        }
      }
      it("renders for Restricted Patients API with optional data missing") {
        val testInput = mapOf(
          "prisonerNumber" to "A1234AA",
          "supportingPrisonDescription" to "HMP Exeter",
          "hospitalLocationDescription" to "Weston Park Hospital",
          "dischargeTime" to "2024-09-05T08:50:44.19812",
        )
        writeAndThenReadPdf(testInput).use {
          val page = it.getPage(2)
          val text = PdfTextExtractor.getTextFromPage(page)
          assertThat(text).contains("Restricted Patients")
          assertThat(text).contains("Prison number A1234AA")
          assertThat(text).contains("Discharge time 05 September 2024, 8:50:44 am")
          assertThat(text).contains("Hospital location Weston Park Hospital")
          assertThat(text).contains("Supporting prison HMP Exeter")
          assertThat(text).contains("Comments No Data Held")
        }
      }
      it("renders for Restricted Patients API with no data held") {
        writeAndThenReadPdf(null).use {
          val page = it.getPage(2)
          val text = PdfTextExtractor.getTextFromPage(page)
          assertThat(text).contains("Restricted Patients")
          assertThat(text).doesNotContain("Prison number")
          assertThat(text).contains("No data held")
        }
      }
    }
  },
)
