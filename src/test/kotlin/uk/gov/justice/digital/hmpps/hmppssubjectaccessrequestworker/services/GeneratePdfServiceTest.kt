package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Document
import com.itextpdf.text.pdf.PdfWriter
import io.kotest.core.spec.style.DescribeSpec
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.CustomHeader
import java.io.ByteArrayOutputStream

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
        verify(mockDocument, Mockito.times(1)).add(any())
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
      it("returns a ByteArrayOutputStream") {
        val testResponseObject: Map<String, Any> = mapOf("Dummy" to "content")
        val mockDocument = Mockito.mock(Document::class.java)
        val mockPdfService = Mockito.mock(PdfService::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
        val mockWriter = Mockito.mock(PdfWriter::class.java)
        Mockito.`when`(mockPdfService.getPdfWriter(mockDocument, mockStream)).thenReturn(mockWriter)
        Mockito.`when`(mockWriter.pageNumber).thenReturn(1)
        val stream = generatePdfService.execute(testResponseObject, "NDELIUS ID: EGnDeliusID", "EGsarID", mockDocument, mockStream)
        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("calls iText open, add and close") {
        val testResponseObject: Map<String, Any> =
          mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()))
        val mockDocument = Mockito.mock(Document::class.java)
        val mockPdfService = Mockito.mock(PdfService::class.java)
        val mockPdfWriter = Mockito.mock(PdfWriter::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
        val mockHeader = Mockito.mock(CustomHeader::class.java)
        Mockito.`when`(mockPdfService.getPdfWriter(mockDocument, mockStream)).thenReturn(mockPdfWriter)
        Mockito.`when`(mockPdfService.getCustomHeader("NDELIUS ID: EGnDeliusID", "EGsarID")).thenReturn(mockHeader)
        Mockito.`when`(mockPdfService.setEvent(mockPdfWriter, mockHeader)).thenReturn(0)

        generatePdfService.execute(testResponseObject, "NDELIUS ID: EGnDeliusID", "EGsarID", mockDocument, mockStream)
        verify(mockPdfService, Mockito.times(1)).getPdfWriter(mockDocument, mockStream)
        verify(mockDocument, Mockito.times(1)).open()
        verify(mockDocument, Mockito.times(1)).add(any())
        verify(mockDocument, Mockito.times(1)).close()
      }
      it("adds header to a PDF") {
        val testResponseObject: Map<String, Any> =
          mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()))
        val mockDocument = Mockito.mock(Document::class.java)
        val mockPdfService = Mockito.mock(PdfService::class.java)
        val mockPdfWriter = Mockito.mock(PdfWriter::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
        val mockWriter = Mockito.mock(PdfWriter::class.java)
        val mockHeader = Mockito.mock(CustomHeader::class.java)
        Mockito.`when`(mockPdfService.getCustomHeader("NDELIUS ID: EGnDeliusID", "EGsarID")).thenReturn(mockHeader)
        Mockito.`when`(mockPdfService.setEvent(mockPdfWriter, mockHeader)).thenReturn(0)
        Mockito.`when`(mockPdfService.getPdfWriter(mockDocument, mockStream)).thenReturn(mockWriter)
        Mockito.`when`(mockWriter.pageNumber).thenReturn(1)
        generatePdfService.execute(testResponseObject, "NDELIUS ID: EGnDeliusID", "EGsarID", mockDocument, mockStream)
        verify(mockPdfService, Mockito.times(1)).getCustomHeader("NDELIUS ID: EGnDeliusID", "EGsarID")
      }

      it("adds rear page") {
        val testResponseObject = mutableMapOf<String, Any>()
        val mockDocument = Mockito.mock(Document::class.java)
        val mockPdfService = Mockito.mock(PdfService::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
        val mockWriter = Mockito.mock(PdfWriter::class.java)
        Mockito.`when`(mockPdfService.getPdfWriter(mockDocument, mockStream)).thenReturn(mockWriter)
        Mockito.`when`(mockWriter.pageNumber).thenReturn(1)
        generatePdfService.execute(testResponseObject, "NDELIUS ID: EGnDeliusID", "EGsarID", mockDocument, mockStream)
        verify(mockPdfService, Mockito.times(1)).addRearPage(document = any(), font = any(), numPages = any())
      }

    }
  },
)
