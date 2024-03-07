package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Document
import io.kotest.core.spec.style.DescribeSpec
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import java.io.ByteArrayOutputStream

@ActiveProfiles("test")
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(GeneratePdfService::class)],
)
class GeneratePdfServiceTest(
  generatePdfService: GeneratePdfService,
) : DescribeSpec(
  {
    describe("generatePdfService") {
      it("returns a ByteArrayOutputStream") {
        val testResponseObject: Map<String, Any> = mapOf("Dummy" to "content")
        val stream = generatePdfService.execute(testResponseObject)
        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("calls iText open, add and close") {
        val testResponseObject: Map<String, Any> = mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()))
        val mockDocument = Mockito.mock(Document::class.java)
        val mockPdfService = Mockito.mock(PdfService::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
        Mockito.`when`(mockPdfService.getPdfWriter(mockDocument, mockStream)).thenReturn(0)

        generatePdfService.execute(testResponseObject, mockDocument, mockStream, mockPdfService)
        verify(mockDocument, Mockito.times(1)).open()
        verify(mockPdfService, Mockito.times(1)).getPdfWriter(mockDocument, mockStream)
        verify(mockDocument, Mockito.times(1)).add(any())
        verify(mockDocument, Mockito.times(1)).close()
      }

      it("handles no data being extracted") {
        val testResponseObject = mutableMapOf<String, Any>()
        val mockDocument = Mockito.mock(Document::class.java)
        val mockPdfService = Mockito.mock(PdfService::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
        Assertions.assertThat(testResponseObject).isEqualTo(emptyMap<Any, Any>())
        generatePdfService.execute(testResponseObject, mockDocument, mockStream, mockPdfService)
        val stream = generatePdfService.execute(testResponseObject)
        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }
    }
  },
)
