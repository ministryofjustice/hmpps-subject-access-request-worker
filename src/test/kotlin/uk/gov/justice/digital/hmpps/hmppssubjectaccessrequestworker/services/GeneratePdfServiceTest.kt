package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import io.kotest.core.spec.style.DescribeSpec
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.time.LocalDate

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
        Mockito.mock(Document::class.java)
        Mockito.mock(ByteArrayOutputStream::class.java)
        val stream = generatePdfService.execute(testResponseObject, "EGnomisID", "EGnDeliusID", "EGsarID", LocalDate.of(1999, 12, 30), LocalDate.of(2010, 12, 30), mutableMapOf("service1" to "service1url"))
        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("returns the same stream") {
        val testResponseObject: Map<String, Any> = mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()))
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
        val result = generatePdfService.execute(testResponseObject, "", "", "", LocalDate.of(1999, 12, 30), LocalDate.of(2010, 12, 30), mutableMapOf("service1" to "service1url"), mockStream)
        Assertions.assertThat(result).isEqualTo(mockStream)
      }

      it("handles no data being extracted") {
        val testResponseObject = mutableMapOf<String, Any>()
        Mockito.mock(Document::class.java)
        Mockito.mock(ByteArrayOutputStream::class.java)
        Assertions.assertThat(testResponseObject).isEqualTo(emptyMap<Any, Any>())
        val stream = generatePdfService.execute(testResponseObject, "", "", "", LocalDate.of(1999, 12, 30), LocalDate.of(2010, 12, 30), mutableMapOf("service1" to "service1url"))
        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("adds rear page with correct text") {
        val writer = PdfWriter(FileOutputStream("dummy.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        val font = PdfFontFactory.createFont(StandardFonts.COURIER)
        mockDocument.add(Paragraph("Text so that the page isn't empty").setFont(font).setFontSize(20f))
        Assertions.assertThat(mockPdfDocument.numberOfPages).isEqualTo(1)
        generatePdfService.addRearPage(mockPdfDocument, mockDocument, mockPdfDocument.numberOfPages)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy.pdf"))
        val page = reader.getPage(1)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("End of Subject Access Request Report")
        Assertions.assertThat(text).contains("Total pages: 1")
      }

      it("writes data to a PDF") {
        val testResponseObject: Map<String, Any> =
          mapOf(
            "fake-service-name-1" to mapOf("fake-prisoner-search-property-eg-age" to "dummy age", "fake-prisoner-search-property-eg-name" to "dummy name"),
            "fake-service-name-2" to mapOf("fake-prisoner-search-property-eg-age" to "dummy age", "fake-prisoner-search-property-eg-name" to "dummy name"),
          )
        val writer = PdfWriter(FileOutputStream("dummy.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        mockDocument.setMargins(50F, 50F, 100F, 50F)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy.pdf"))
        val page = reader.getPage(1)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("fake-service-name-1")
        Assertions.assertThat(text).contains("fake-service-name-2")
      }

      it("adds cover page to a PDF") {
        val writer = PdfWriter(FileOutputStream("dummy.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        val font = PdfFontFactory.createFont(StandardFonts.COURIER)
        mockDocument.add(Paragraph("Text so that the page isn't empty").setFont(font).setFontSize(20f))
        Assertions.assertThat(mockPdfDocument.numberOfPages).isEqualTo(1)
        generatePdfService.addCoverpage(mockPdfDocument, mockDocument, "mockNomisNumber", null, "mockCaseReference", LocalDate.now(), LocalDate.now(), mutableMapOf("mockService" to "mockServiceUrl"))
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy.pdf"))
        val page = reader.getPage(1)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("SUBJECT ACCESS REQUEST REPORT")
        Assertions.assertThat(text).contains("NOMIS ID: mockNomisNumber")
      }

      it("converts data to YAML format") {
        val testInput = mapOf(
          "testDateText" to "Test",
          "testDataNumber" to 99,
          "testDataArray" to arrayOf(1, 2, 3, 4, 5),
          "testDataMap" to mapOf("a" to "1", "b" to "2"),
          "testDataNested" to mapOf(
            "a" to "test",
            "b" to 2,
            "c" to arrayOf("alpha", "beta", "gamma", "delta"),
            "d" to mapOf("x" to 1, "z" to 2),
          ),
          "testDataDeepNested" to mapOf(
            "a" to mapOf(
              "b" to mapOf(
                "c" to mapOf(
                  "d" to mapOf(
                    "e" to mapOf(
                      "f" to mapOf(
                        "g" to mapOf(
                          "h" to mapOf(
                            "i" to mapOf(
                              "j" to "k",
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        )
        val testResponseObject: Map<String, Any> = mapOf("fake-service-name" to testInput)
        val writer = PdfWriter(FileOutputStream("dummy.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        mockDocument.setMargins(50F, 50F, 100F, 50F)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy.pdf"))
        val page = reader.getPage(1)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("fake-service-name")
        Assertions.assertThat(text).contains("testDateText: \"Test\"")
        Assertions.assertThat(text).contains("testDataNumber: 99")
        Assertions.assertThat(text).contains("testDataArray: \n- 1 \n- 2 \n- 3 \n- 4 \n- 5 ")
        Assertions.assertThat(text).contains("testDataMap: \n  a: \"1\" \n  b: \"2\" ")
        Assertions.assertThat(text).contains(
          "testDataNested: \n" +
            "  a: \"test\" \n" +
            "  b: 2 \n" +
            "  c: \n  - \"alpha\" \n  - \"beta\" \n  - \"gamma\" \n  - \"delta\" \n" +
            "  d: \n    x: 1 \n    z: 2 ",
        )
        Assertions.assertThat(text).contains(
          "testDataDeepNested: \n" +
            "  a: \n" +
            "    b: \n" +
            "      c: \n" +
            "        d: \n" +
            "          e: \n" +
            "            f: \n" +
            "              g: \n" +
            "                h: \n" +
            "                  i: \n" +
            "                    j: \"k\" ",
        )
      }
    }
  },
)
