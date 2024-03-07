package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Document
import com.itextpdf.text.pdf.PdfWriter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ActiveProfiles("test")
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(GetSubjectAccessRequestDataService::class)],
)
class GetSubjectAccessRequestDataServiceTest(
  getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService,
  @MockBean val mockGenericHmppsApiGateway: GenericHmppsApiGateway,
) : DescribeSpec(
  {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dateTo = "30/01/2023"
    val dateToFormatted = LocalDate.parse(dateTo, formatter)

    beforeEach {
      Mockito.reset(mockGenericHmppsApiGateway)

      whenever(mockGenericHmppsApiGateway.getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)).thenReturn(
        mapOf(
          "content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()),
        ),
      )

      whenever(mockGenericHmppsApiGateway.getSarData(serviceUrl = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)).thenReturn(
        mapOf(
          "content" to mapOf<String, Any>("fake-indexer-property" to emptyMap<String, Any>()),
        ),
      )
    }

    describe("getSubjectAccessRequestData") {
      it("calls getSarData with given arguments, including service URL") {
        getSubjectAccessRequestDataService.execute(services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk", nomisId = "A1234AA", dateTo = dateToFormatted)

        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
      }

      it("calls the gateway separately for each service given") {
        getSubjectAccessRequestDataService.execute(services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk,fake-hmpps-prisoner-search-indexer, https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", nomisId = "A1234AA", dateTo = dateToFormatted)

        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
      }

      it("returns upstream API response data with data mapped to API from which it was retrieved") {
        val response = getSubjectAccessRequestDataService.execute(services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk,fake-hmpps-prisoner-search-indexer, https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", nomisId = "A1234AA", dateTo = dateToFormatted)

        response.keys.shouldBe(setOf("fake-hmpps-prisoner-search", "fake-hmpps-prisoner-search-indexer"))
        response["fake-hmpps-prisoner-search"].toString().shouldContain("fake-prisoner-search-property")
        response["fake-hmpps-prisoner-search-indexer"].toString().shouldContain("fake-indexer-property")
      }
    }

    describe("getSubjectAccessRequestData generatePDF") {
      it("returns a ByteArrayOutputStream") {
        val testResponseObject: Map<String, Any> = mapOf("Dummy" to "content")
        val stream = getSubjectAccessRequestDataService.generatePDF(testResponseObject)
        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("calls iText open, add and close") {
        val testResponseObject: Map<String, Any> =
          mapOf("content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()))
        val mockDocument = Mockito.mock(Document::class.java)
        val mockPdfService = Mockito.mock(PdfService::class.java)
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)
        Mockito.`when`(mockPdfService.getPdfWriter(mockDocument, mockStream)).thenReturn(0)

        getSubjectAccessRequestDataService.generatePDF(testResponseObject, mockDocument, mockStream, mockPdfService)
        verify(mockDocument, Mockito.times(1)).open()
        verify(mockPdfService, Mockito.times(1)).getPdfWriter(mockDocument, mockStream)
        verify(mockDocument, Mockito.times(1)).add(any())
        verify(mockDocument, Mockito.times(1)).close()
      }
    }
    describe("getSubjectAccessRequestData addData") {

      it("writes data to a PDF") {
        val testResponseObject: Map<String, Any> =
          mapOf("fake-service-name-1" to mapOf("fake-prisoner-search-property-eg-age" to "dummy age", "fake-prisoner-search-property-eg-name" to "dummy name"),
            "fake-service-name-2" to mapOf("fake-prisoner-search-property-eg-age" to "dummy age", "fake-prisoner-search-property-eg-name" to "dummy name"))
        val mockDocument = Document()
        PdfWriter.getInstance(mockDocument, FileOutputStream("dummy.pdf"))
        mockDocument.setMargins(50F, 50F, 100F, 50F)
        mockDocument.open()
        getSubjectAccessRequestDataService.addData(mockDocument, testResponseObject)
        mockDocument.close()
      }
    }
  },
)
