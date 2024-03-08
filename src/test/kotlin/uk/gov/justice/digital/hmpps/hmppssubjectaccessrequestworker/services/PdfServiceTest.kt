package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.FontFactory
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfWriter
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import io.kotest.core.spec.style.DescribeSpec
import org.assertj.core.api.Assertions
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.io.FileOutputStream

@ActiveProfiles("test")
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(PdfService::class)],
)
class PdfServiceTest(pdfService: PdfService): DescribeSpec( {
  describe("addRearPage")
  {
    it("adds page with correct text") {
      val mockDocument = Document()
      val writer = PdfWriter.getInstance(mockDocument, FileOutputStream("dummy.pdf"))
      mockDocument.open()
      val font = FontFactory.getFont(FontFactory.COURIER, 20f, BaseColor.BLACK)
      mockDocument.add(Chunk("Text so that the page isn't empty", font))
      writer.isPageEmpty = false
      Assertions.assertThat(writer.pageNumber).isEqualTo(1)
      pdfService.addRearPage(
        mockDocument, font, writer.pageNumber
      )
      mockDocument.close()
      val reader = PdfReader("dummy.pdf")
      val text = PdfTextExtractor.getTextFromPage(reader, 2)
      Assertions.assertThat(text).contains("End of Subject Access Request Report")
      Assertions.assertThat(text).contains("Total pages: 1")
    }
  }
}
)