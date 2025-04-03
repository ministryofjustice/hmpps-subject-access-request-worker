package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.AreaBreakType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.CustomHeaderEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.time.LocalDate

class GeneratePdfServiceTest : BaseGeneratePdfTest() {

  @BeforeEach
  fun setup() {
    whenever(dateService.now()).thenReturn(LocalDate.now())
  }

  @Test
  fun `generatePdfService adds rear page with correct text`() {
    val pdfDocument = createPdfDocument("dummy.pdf")
    val document = Document(pdfDocument)

    document.add(Paragraph("This page represents the upstream data pages"))
    val numberOfPagesWithoutRearAndCoverPage = pdfDocument.numberOfPages
    document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
      .add(Paragraph("This page represents the internal cover page"))
    generatePdfService.addRearPage(pdfDocument, document, numberOfPagesWithoutRearAndCoverPage)
    val numberOfPagesWithRearAndCoverPage = pdfDocument.numberOfPages
    document.close()

    getGeneratedPdfDocument("dummy.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(3))
      assertThat(numberOfPagesWithoutRearAndCoverPage).isEqualTo(1)
      assertThat(numberOfPagesWithRearAndCoverPage).isEqualTo(3)
      assertThat(text).contains("End of Subject Access Request Report")
      assertThat(text).contains("Total pages: 3")
    }
  }

  @Test
  fun `generatePdfService writes data to a PDF`() {
    val pdfDocument = createPdfDocument("dummy.pdf")
    val document = Document(pdfDocument)
    document.setMargins(50F, 50F, 100F, 50F)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()

    getGeneratedPdfDocument("dummy.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(2))
      val text2 = PdfTextExtractor.getTextFromPage(pdf.getPage(3))

      assertThat(text).contains("Service for testing")
      assertThat(text2).contains("Service for testing 2")
    }
  }

  @Test
  fun `generatePdfService adds internal cover page to a PDF`() {
    val fullDocumentWriter = PdfWriter(FileOutputStream(resolveTempFilePath("dummy.pdf")))

    val mainPdfStream = ByteArrayOutputStream()
    val pdfDocument = PdfDocument(PdfWriter(mainPdfStream))
    val document = Document(pdfDocument)
    document.add(Paragraph("Text so that the page isn't empty"))
    val numberOfPagesWithoutCoverpage = pdfDocument.numberOfPages
    document.close()

    // Add cover page
    val coverPdfStream = ByteArrayOutputStream()
    val coverPage = PdfDocument(PdfWriter(coverPdfStream))
    val coverPageDocument = Document(coverPage)

    generatePdfService.addInternalCoverPage(
      document = coverPageDocument,
      subjectName = "LASTNAME, Firstname",
      nomisId = "nomisNumber",
      ndeliusCaseReferenceId = null,
      sarCaseReferenceNumber = "CaseReference",
      dateFrom = LocalDate.now(),
      dateTo = LocalDate.now(),
      numPages = numberOfPagesWithoutCoverpage,
    )
    coverPageDocument.close()
    val fullDocument = PdfDocument(fullDocumentWriter)
    val merger = PdfMerger(fullDocument)
    val cover = PdfDocument(PdfReader(ByteArrayInputStream(coverPdfStream.toByteArray())))
    val mainContent = PdfDocument(PdfReader(ByteArrayInputStream(mainPdfStream.toByteArray())))
    merger.merge(cover, 1, 1)
    merger.merge(mainContent, 1, mainContent.numberOfPages)
    cover.close()
    mainContent.close()

    // Test
    assertThat(numberOfPagesWithoutCoverpage).isEqualTo(1)
    assertThat(fullDocument.numberOfPages).isEqualTo(2)
    fullDocument.close()

    getGeneratedPdfDocument("dummy.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(1))
      assertThat(text).contains("SUBJECT ACCESS REQUEST REPORT")
      assertThat(text).contains("NOMIS ID: nomisNumber")
      assertThat(text).contains("Name: LASTNAME, Firstname")
      assertThat(text).contains("Total Pages: 3")
    }
  }

  @Test
  fun `generatePdfService adds internal contents page to a PDF`() {
    val writer = PdfWriter(FileOutputStream(resolveTempFilePath("dummy.pdf")))
    val pdfDocument = PdfDocument(writer)
    val document = Document(pdfDocument)

    generatePdfService.addInternalContentsPage(pdfDocument, document, serviceList)

    document.close()
    getGeneratedPdfDocument("dummy.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(1))

      assertThat(text).contains("CONTENTS")
      assertThat(text).contains("INTERNAL ONLY")
    }
  }

  @Test
  fun `generatePdfService adds external coverpage for recipient to a PDF`() {
    val writer = PdfWriter(FileOutputStream(resolveTempFilePath("dummy.pdf")))
    val pdfDocument = PdfDocument(writer)
    val document = Document(pdfDocument)
    generatePdfService.addExternalCoverPage(
      pdfDocument,
      document,
      "LASTNAME, FIRSTNAME",
      "nomisNumber",
      null,
      "CaseReference",
      LocalDate.now(),
      LocalDate.now(),
    )
    document.close()

    getGeneratedPdfDocument("dummy.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(2))

      assertThat(text).contains("SUBJECT ACCESS REQUEST REPORT")
      assertThat(text).contains("NOMIS ID: nomisNumber")
      assertThat(text).contains("Name: LASTNAME, FIRSTNAME")
    }
  }

  @Test
  fun `generatePdfService converts data to YAML format in the event of no template`() {
    val testResponseObject = listOf(DpsService(name = "yaml-service-name", content = noTemplateServiceYaml))
    generateSubjectAccessRequestPdf("dummy-yaml.pdf", testResponseObject)

    getGeneratedPdfDocument("dummy-yaml.pdf").use { pdf ->
      val text = PdfTextExtractor.getTextFromPage(pdf.getPage(2))

      assertThat(text).contains("Yaml service name")
      assertThat(text).contains("Test date text: \"Test\"")
      assertThat(text).contains("Test data number: 99")
      assertThat(text).contains("Test data array: \n  - 1 \n  - 2 \n  - 3 \n  - 4 \n  - 5 ")
      assertThat(text).contains("Test data map: \n  A: \"1\" \n  B: \"2\" ")
      assertThat(text).contains(
        "Test data nested: \n" +
          "  A: \"test\" \n" +
          "  B: 2 \n" +
          "  C: \n    - \"alpha\" \n    - \"beta\" \n    - \"gamma\" \n    - \"delta\" \n" +
          "  D: \n    X: 1 \n    Z: 2 ",
      )
      assertThat(text).contains(
        "Test data deep nested: \n" +
          "  A: \n" +
          "    B: \n" +
          "      C: \n" +
          "        D: \n" +
          "          E: \n" +
          "            F: \n" +
          "              G: \n" +
          "                H: \n" +
          "                  I: \n" +
          "                    J: \"k\" ",
      )
    }
  }

  private val noTemplateServiceYaml = mapOf(
    "testDateText" to "Test",
    "testDataNumber" to 99,
    "testDataArray" to arrayListOf(1, 2, 3, 4, 5),
    "testDataMap" to mapOf("a" to "1", "b" to "2"),
    "testDataNested" to mapOf(
      "a" to "test",
      "b" to 2,
      "c" to arrayListOf("alpha", "beta", "gamma", "delta"),
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

  @Test
  fun `generatePdfService creates a full PDF report`() {
    val testContentObject = listOf(DpsService(name = "fake-service-name", content = fullReportServiceData))

    // PDF set up
    val mainPdfStream = ByteArrayOutputStream()
    val pdfDocument = PdfDocument(PdfWriter(mainPdfStream))
    val document = Document(pdfDocument)

    // Add content and rear pages
    val testDataFromServices = listOf(
      DpsService(
        name = "fake-service-name-1",
        content = mapOf(
          "fake-prisoner-search-property-eg-age" to "dummy age",
          "fake-prisoner-search-property-eg-name" to "dummy name",
        ),
      ),
      DpsService(
        name = "fake-service-name-2",
        content = mapOf(
          "fake-prisoner-search-property-eg-age" to "dummy age",
          "fake-prisoner-search-property-eg-name" to "dummy name",
        ),
      ),
    )
    generatePdfService.addInternalContentsPage(
      pdfDocument = pdfDocument,
      document = document,
      services = testDataFromServices,
    )
    generatePdfService.addExternalCoverPage(
      pdfDocument = pdfDocument,
      document = document,
      subjectName = "LASTNAME, Firstname",
      nomisId = "nomisNumber",
      ndeliusCaseReferenceId = null,
      sarCaseReferenceNumber = "CaseReference",
      dateFrom = LocalDate.now(),
      dateTo = LocalDate.now(),
    )
    pdfDocument.addEventHandler(
      PdfDocumentEvent.END_PAGE,
      CustomHeaderEventHandler(pdfDocument, document, "NOMIS ID: nomisNumber", "LASTNAME, Firstname"),
    )
    generatePdfService.addData(pdfDocument, document, testContentObject)
    val numPages = pdfDocument.numberOfPages
    generatePdfService.addRearPage(pdfDocument, document, numPages)
    document.close()

    // Add cover page
    val coverPdfStream = ByteArrayOutputStream()
    val coverPage = PdfDocument(PdfWriter(coverPdfStream))
    val coverPageDocument = Document(coverPage)
    generatePdfService.addInternalCoverPage(
      document = coverPageDocument,
      subjectName = "LASTNAME, Firstname",
      nomisId = "nomisNumber",
      ndeliusCaseReferenceId = null,
      sarCaseReferenceNumber = "CaseReference",
      dateFrom = LocalDate.now(),
      dateTo = LocalDate.now(),
      numPages = numPages,
    )
    coverPageDocument.close()

    val fullDocument = createPdfDocument("dummy.pdf")
    val merger = PdfMerger(fullDocument)
    val cover = PdfDocument(PdfReader(ByteArrayInputStream(coverPdfStream.toByteArray())))
    val mainContent = PdfDocument(PdfReader(ByteArrayInputStream(mainPdfStream.toByteArray())))
    merger.merge(cover, 1, 1)
    merger.merge(mainContent, 1, mainContent.numberOfPages)
    cover.close()
    mainContent.close()

    // Test
    assertThat(fullDocument.numberOfPages).isEqualTo(5)
    fullDocument.close()

    getGeneratedPdfDocument("dummy.pdf").use { pdf ->
      val coverPageText = PdfTextExtractor.getTextFromPage(pdf.getPage(1))
      val dataPageText = PdfTextExtractor.getTextFromPage(pdf.getPage(4))

      assertThat(coverPageText).contains("SUBJECT ACCESS REQUEST REPORT")
      assertThat(coverPageText).contains("NOMIS ID: nomisNumber")
      assertThat(dataPageText).contains("Name: LASTNAME, Firstname")
      assertThat(dataPageText).contains("NOMIS ID: nomisNumber")
    }
  }

  private val fullReportServiceData = mapOf(
    "testDateText" to "Test",
    "testDataNumber" to 99,
    "testDataArray" to arrayListOf(1, 2, 3, 4, 5),
    "testDataMap" to mapOf("a" to "1", "b" to "2"),
    "testDataNested" to mapOf(
      "a" to "test",
      "b" to 2,
      "c" to arrayListOf("alpha", "beta", "gamma", "delta"),
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

  @Test
  fun `generatePdfService preProcessData processValue if input is a string number null`() {
    assertThat(generatePdfService.preProcessData("testInput")).isEqualTo("testInput")
    assertThat(generatePdfService.preProcessData(5)).isEqualTo(5)
    assertThat(generatePdfService.preProcessData(null)).isEqualTo("No data held") // - How does bodyToMono handle null?
  }

  @Test
  fun `generatePdfService preProcessData preprocesses correctly for simple string object`() {
    val testInput = mapOf("testKey" to "testValue")
    val testOutput = mapOf("Test key" to "testValue")

    assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
  }

  @Test
  fun `generatePdfService preProcessData preprocesses correctly for a map of maps`() {
    val testInput = mapOf("parentTestKey" to mapOf("nestedTestKey" to "nestedTestValue"))
    val testOutput = mapOf("Parent test key" to mapOf("Nested test key" to "nestedTestValue"))

    assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
  }

  @Test
  fun `generatePdfService preProcessData preprocesses correctly for array of objects`() {
    val testInput = arrayListOf(mapOf("testKeyOne" to "testValueOne"), mapOf("testKeyTwo" to "testValueTwo"))
    val testOutput = arrayListOf(mapOf("Test key one" to "testValueOne"), mapOf("Test key two" to "testValueTwo"))

    assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
  }

  @Test
  fun `generatePdfService preProcessData preprocesses correctly for a map of arrays of maps of arrays`() {
    val testInput = mapOf("parentTestKey" to arrayListOf(mapOf("nestedTestKey" to arrayListOf("testString"))))
    val testOutput = mapOf("Parent test key" to arrayListOf(mapOf("Nested test key" to arrayListOf("testString"))))

    assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
  }

  @Test
  fun `generatePdfService preProcessData replaces null values in a simple string object`() {
    val testInput = mapOf("testKey" to null)
    val testOutput = mapOf("Test key" to "No data held")

    assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
  }

  @Test
  fun `generatePdfService preProcessData replaces null values in a map of maps`() {
    val testInput = mapOf("parentTestKey" to mapOf("nestedTestKey" to "null"))
    val testOutput = mapOf("Parent test key" to mapOf("Nested test key" to "No data held"))

    assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
  }

  @Test
  fun `generatePdfService preProcessData replaces null values in arrays of maps`() {
    val testInput = arrayListOf(mapOf("testKeyOne" to "testValueOne"), mapOf("testKeyTwo" to "null"))
    val testOutput = arrayListOf(mapOf("Test key one" to "testValueOne"), mapOf("Test key two" to "No data held"))

    assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
  }

  @Test
  fun `generatePdfService preProcessData replaces empty array lists`() {
    val testInput = arrayListOf<Any?>()
    val testOutput = "No data held"

    assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
  }

  @Test
  fun `generatePdfService preProcessData replaces null values and arrays in a map of arrays of maps of arrays`() {
    val testInput = mapOf("parentTestKey" to arrayListOf<Any?>())
    val testOutput = mapOf("Parent test key" to "No data held")

    assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
  }

  @Test
  fun `generatePdfService preProcessData processValue replaces dates in various formats`() {
    val testCases = arrayListOf(
      mapOf(
        "input" to "2024-05-01",
        "expected" to "01 May 2024",
      ),
      mapOf(
        "input" to "01/05/2024",
        "expected" to "01 May 2024",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59+01:00",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59Z",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59Z+01:00",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123+01:00",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123Z",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123Z+01:00",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123456",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123456+01:00",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123456Z",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123456Z+01:00",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123456789",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123456789+01:00",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123456789Z",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "2024-05-01T17:43:59.123456789Z+01:00",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
      mapOf(
        "input" to "01/05/2024 17:43",
        "expected" to "01 May 2024, 5:43 pm",
      ),
      mapOf(
        "input" to "01/05/2024 17:43:59",
        "expected" to "01 May 2024, 5:43:59 pm",
      ),
    )

    testCases.forEach { test ->
      assertThat(generatePdfService.processValue(test["input"])).isEqualTo(test["expected"])
    }
  }

  private val serviceList = listOf(
    DpsService(
      name = "service-for-testing",
      content = mapOf(
        "test-service-eg-age" to "service age",
        "test-service-eg-name" to "service name",
      ),
    ),
    DpsService(
      name = "service-for-testing-2",
      content = mapOf(
        "test-service-2-eg-age" to "service-2 age",
        "test-service-2-eg-name" to "service-2 name",
      ),
    ),
  )
}
