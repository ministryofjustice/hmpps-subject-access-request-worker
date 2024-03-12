package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
<<<<<<< HEAD
import com.itextpdf.text.FontFactory
=======
>>>>>>> origin
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.PdfWriter
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import io.kotest.core.spec.style.DescribeSpec
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

@ActiveProfiles("test")
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(GeneratePdfService::class)],
)
class GeneratePdfServiceTest(
  @Autowired val generatePdfService: GeneratePdfService,
) : DescribeSpec(
  {
    describe("generatePdfService") {
      it("returns a ByteArrayOutputStream") {
        val testResponseObject: Map<String, Any> = mapOf("Dummy" to "content")
        val mockDocument = Mockito.mock(Document::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)

        val stream = generatePdfService.execute(testResponseObject, "NDELIUS ID: EGnDeliusID", "EGsarID", mockDocument, mockStream)

        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("calls iText open, add and close") {
        val testResponseObject: Map<String, Any> = mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()))
        val mockDocument = Mockito.mock(Document::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)

        generatePdfService.execute(testResponseObject, "", "", mockDocument, mockStream)

        verify(mockDocument, Mockito.times(1)).open()
        verify(mockDocument, Mockito.times(3)).add(any())
        verify(mockDocument, Mockito.times(1)).close()
      }

      it("handles no data being extracted") {
        val testResponseObject = mutableMapOf<String, Any>()
        val mockDocument = Mockito.mock(Document::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
        Assertions.assertThat(testResponseObject).isEqualTo(emptyMap<Any, Any>())
        val stream = generatePdfService.execute(testResponseObject, "", "", mockDocument, mockStream)
        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("adds rear page with correct text") {
        val mockDocument = Document()
        val writer = PdfWriter.getInstance(mockDocument, FileOutputStream("dummy.pdf"))
        mockDocument.open()
        val font = FontFactory.getFont(FontFactory.COURIER, 20f, BaseColor.BLACK)
        mockDocument.add(Chunk("Text so that the page isn't empty", font))
        writer.isPageEmpty = false
        Assertions.assertThat(writer.pageNumber).isEqualTo(1)
        generatePdfService.addRearPage(mockDocument, writer.pageNumber)
        mockDocument.close()
        val reader = PdfReader("dummy.pdf")
        val text = PdfTextExtractor.getTextFromPage(reader, 2)
        Assertions.assertThat(text).contains("End of Subject Access Request Report")
        Assertions.assertThat(text).contains("Total pages: 1")
      }

      it("writes data to a PDF") {
        val testResponseObject: Map<String, Any> =
          mapOf(
            "fake-service-name-1" to mapOf("fake-prisoner-search-property-eg-age" to "dummy age", "fake-prisoner-search-property-eg-name" to "dummy name"),
            "fake-service-name-2" to mapOf("fake-prisoner-search-property-eg-age" to "dummy age", "fake-prisoner-search-property-eg-name" to "dummy name"),
          )
        val mockDocument = Document()
        PdfWriter.getInstance(mockDocument, FileOutputStream("dummy.pdf"))
        mockDocument.setMargins(50F, 50F, 100F, 50F)
        mockDocument.open()
        generatePdfService.addData(mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfReader("dummy.pdf")
        val text = PdfTextExtractor.getTextFromPage(reader, 1)
        Assertions.assertThat(text).contains("fake-service-name-1")
        Assertions.assertThat(text).contains("fake-service-name-2")
      }
    }
  },
)
