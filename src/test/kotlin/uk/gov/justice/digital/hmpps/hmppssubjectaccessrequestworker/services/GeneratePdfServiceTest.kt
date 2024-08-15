package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.AreaBreakType
import io.kotest.core.spec.style.DescribeSpec
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.CustomHeaderEventHandler
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import java.io.ByteArrayInputStream
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
        val testResponseObject: List<DpsService> = listOf(DpsService(name = "test-service", content = mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
        Mockito.mock(Document::class.java)
        Mockito.mock(ByteArrayOutputStream::class.java)

        val stream = generatePdfService.execute(testResponseObject, "EGnomisID", "EGnDeliusID", "EGsarID", "", LocalDate.of(1999, 12, 30), LocalDate.of(2010, 12, 30))

        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("returns the same stream") {
        val testResponseObject: List<DpsService> = listOf(DpsService(name = "test-service", content = mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>())))
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)

        val result = generatePdfService.execute(testResponseObject, "", "", "", "", LocalDate.of(1999, 12, 30), LocalDate.of(2010, 12, 30), mockStream)

        Assertions.assertThat(result).isEqualTo(mockStream)
      }

      it("handles no data being extracted") {
        val testResponseObject = listOf(DpsService(name = "test-service", content = null))
        Mockito.mock(Document::class.java)
        Mockito.mock(ByteArrayOutputStream::class.java)
        Assertions.assertThat(testResponseObject[0].content).isNull()

        val stream = generatePdfService.execute(testResponseObject, "", "", "", "", LocalDate.of(1999, 12, 30), LocalDate.of(2010, 12, 30))

        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("adds rear page with correct text") {
        val writer = PdfWriter(FileOutputStream("dummy.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)

        mockDocument.add(Paragraph("This page represents the upstream data pages"))
        val numberOfPagesWithoutRearAndCoverPage = mockPdfDocument.numberOfPages
        mockDocument.add(AreaBreak(AreaBreakType.NEXT_PAGE)).add(Paragraph("This page represents the internal cover page"))
        generatePdfService.addRearPage(mockPdfDocument, mockDocument, numberOfPagesWithoutRearAndCoverPage)
        val numberOfPagesWithRearAndCoverPage = mockPdfDocument.numberOfPages
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy.pdf"))
        val page = reader.getPage(3)
        val text = PdfTextExtractor.getTextFromPage(page)

        Assertions.assertThat(numberOfPagesWithoutRearAndCoverPage).isEqualTo(1)
        Assertions.assertThat(numberOfPagesWithRearAndCoverPage).isEqualTo(3)
        Assertions.assertThat(text).contains("End of Subject Access Request Report")
        Assertions.assertThat(text).contains("Total pages: 3")
      }

      it("writes data to a PDF") {
        val testResponseObject = listOf(
          DpsService(
            name = "service-for-testing",
            content = mapOf(
              "fake-prisoner-search-property-eg-age" to "dummy age",
              "fake-prisoner-search-property-eg-name" to "dummy name",
            ),
          ),
          DpsService(
            name = "service-for-testing-2",
            content = mapOf(
              "fake-prisoner-search-property-eg-age" to "dummy age",
              "fake-prisoner-search-property-eg-name" to "dummy name",
            ),
          ),
        )
        val writer = PdfWriter(FileOutputStream("dummy.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        mockDocument.setMargins(50F, 50F, 100F, 50F)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("Service for testing")
        val page2 = reader.getPage(3)
        val text2 = PdfTextExtractor.getTextFromPage(page2)
        Assertions.assertThat(text2).contains("Service for testing 2")
      }

      describe("cover pages") {
        it("adds internal cover page to a PDF") {
          val fullDocumentWriter = PdfWriter(FileOutputStream("dummy.pdf"))
          val mainPdfStream = ByteArrayOutputStream()
          val mockPdfDocument = PdfDocument(PdfWriter(mainPdfStream))
          val mockDocument = Document(mockPdfDocument)
          mockDocument.add(Paragraph("Text so that the page isn't empty"))
          val numberOfPagesWithoutCoverpage = mockPdfDocument.numberOfPages
          mockDocument.close()

          // Add cover page
          val coverPdfStream = ByteArrayOutputStream()
          val coverPage = PdfDocument(PdfWriter(coverPdfStream))
          val coverPageDocument = Document(coverPage)
          val testDataFromServices = listOf(
            DpsService(
              name = "test-service",
              content = mapOf(
                "fake-prisoner-search-property-eg-age" to "dummy age",
                "fake-prisoner-search-property-eg-name" to "dummy name",
              ),
            ),
            DpsService(
              name = "test-service",
              content = mapOf(
                "fake-prisoner-search-property-eg-age" to "dummy age",
                "fake-prisoner-search-property-eg-name" to "dummy name",
              ),
            ),
          )
          generatePdfService.addInternalCoverPage(
            document = coverPageDocument,
            subjectName = "LASTNAME, Firstname",
            nomisId = "mockNomisNumber",
            ndeliusCaseReferenceId = null,
            sarCaseReferenceNumber = "mockCaseReference",
            dateFrom = LocalDate.now(),
            dateTo = LocalDate.now(),
            services = testDataFromServices,
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
          Assertions.assertThat(numberOfPagesWithoutCoverpage).isEqualTo(1)
          Assertions.assertThat(fullDocument.numberOfPages).isEqualTo(2)
          fullDocument.close()
          val reader = PdfDocument(PdfReader("dummy.pdf"))
          val page = reader.getPage(1)
          val text = PdfTextExtractor.getTextFromPage(page)
          Assertions.assertThat(text).contains("SUBJECT ACCESS REQUEST REPORT")
          Assertions.assertThat(text).contains("NOMIS ID: mockNomisNumber")
          Assertions.assertThat(text).contains("Name: LASTNAME, Firstname")
          Assertions.assertThat(text).contains("Total Pages: 3")
        }

        it("adds internal contents page to a PDF") {
          val writer = PdfWriter(FileOutputStream("dummy.pdf"))
          val mockPdfDocument = PdfDocument(writer)
          val mockDocument = Document(mockPdfDocument)
          val testDataFromServices = listOf(
            DpsService(
              name = "test-service",
              content = mapOf(
                "fake-prisoner-search-property-eg-age" to "dummy age",
                "fake-prisoner-search-property-eg-name" to "dummy name",
              ),
            ),
            DpsService(
              content = mapOf(
                "fake-prisoner-search-property-eg-age" to "dummy age",
                "fake-prisoner-search-property-eg-name" to "dummy name",
              ),
            ),
          )

          generatePdfService.addInternalContentsPage(mockPdfDocument, mockDocument, testDataFromServices)

          mockDocument.close()
          val reader = PdfDocument(PdfReader("dummy.pdf"))
          val page = reader.getPage(1)
          val text = PdfTextExtractor.getTextFromPage(page)

          Assertions.assertThat(text).contains("CONTENTS")
          Assertions.assertThat(text).contains("INTERNAL ONLY")
        }

        it("adds external coverpage for recipient to a PDF") {
          val writer = PdfWriter(FileOutputStream("dummy.pdf"))
          val mockPdfDocument = PdfDocument(writer)
          val mockDocument = Document(mockPdfDocument)
          generatePdfService.addExternalCoverPage(mockPdfDocument, mockDocument, "LASTNAME, FIRSTNAME", "mockNomisNumber", null, "mockCaseReference", LocalDate.now(), LocalDate.now())
          mockDocument.close()

          val reader = PdfDocument(PdfReader("dummy.pdf"))
          val page = reader.getPage(2)
          val text = PdfTextExtractor.getTextFromPage(page)

          Assertions.assertThat(text).contains("SUBJECT ACCESS REQUEST REPORT")
          Assertions.assertThat(text).contains("NOMIS ID: mockNomisNumber")
          Assertions.assertThat(text).contains("Name: LASTNAME, FIRSTNAME")
        }
      }

      it("renders a template if a template exists") {
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "type" to "OMIC",
            "subType" to "OPEN_COMM",
            "creationDateTime" to "2024-01-29T15:00:59.618572",
            "authorName" to "David Middleton",
            "text" to "Testing\r\n\r\nTesting",
            "amendments" to arrayListOf(
              mapOf(
                "creationDateTime" to "2024-01-30T14:54:12.520707",
                "authorName" to "David Middleton",
                "additionalNoteText" to "More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. \r\n\r\n\r\nMore text added. More text added. More text added. ",
              ),
              mapOf(
                "creationDateTime" to "2024-01-30T14:59:46.747803",
                "authorName" to "David Middleton",
                "additionalNoteText" to "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc bibendum sapien et odio suscipit, vitae scelerisque velit vehicula. Nulla mauris purus, semper eu ipsum vel, sollicitudin fringilla est. Ut dui turpis, malesuada non blandit vel, imperdiet ac libero. Nullam aliquam interdum augue nec placerat. Duis euismod, arcu nec dapibus efficitur, eros nisl mollis nibh, semper congue nunc ligula quis purus. Vestibulum in rutrum nibh. Nunc a lectus a eros tristique pharetra et quis velit. Maecenas efficitur justo quis magna porta, ut dapibus enim dapibus. Phasellus vitae turpis at dolor tincidunt cursus non non diam. Mauris accumsan quam mauris, quis aliquam turpis condimentum ac. Quisque sollicitudin cursus sem. Sed ultrices accumsan ipsum, sed auctor ex.\r\n\r\nPraesent dapibus arcu vel metus mollis, ut pulvinar purus sagittis. Curabitur mi ligula, luctus at hendrerit in, vestibulum a orci. Proin lorem orci, sagittis vel molestie sit amet, fermentum in libero. Donec id tortor augue. Aenean vulputate vel nibh sit amet pellentesque. Nam laoreet sodales porttitor. Pellentesque condimentum est ut eros tempus pretium. Vestibulum eget consequat orci. Curabitur pellentesque sem eget neque ullamcorper, vel condimentum neque pellentesque. Nullam fringilla feugiat finibus. Fusce posuere est nec leo viverra sollicitudin. Proin facilisis nibh in ante dignissim volutpat. Donec vitae leo ultricies, suscipit turpis vitae, ultricies augue. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.\r\n\r\nProin efficitur sed lectus et sagittis. Aliquam semper nibh quis molestie cursus. Donec finibus velit ut dictum pretium. Maecenas bibendum odio nec ex sodales, at dapibus dui maximus. Nam ullamcorper est et risus ullamcorper, eu consequat lacus posuere. Cras sit amet est ultrices, maximus neque et, luctus magna. Praesent dignissim augue dignissim, dictum massa id, gravida sapien. Nunc a dolor rutrum, porttitor felis eu, suscipit sapien. Donec semper leo a egestas tempor. Duis non ultricies ex. Fusce orci libero, dignissim at diam pellentesque, pulvinar viverra sem. Integer vel eleifend tortor, ut sagittis lorem. Vivamus rhoncus varius sem, eu sollicitudin nibh laoreet a\r\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc bibendum sapien et odio suscipit, vitae scelerisque velit vehicula. Nulla mauris purus, semper eu ipsum vel, sollicitudin fringilla est. Ut dui turpis, malesuada non blandit vel, imperdiet ac libero. Nullam aliquam interdum augue nec placerat. Duis euismod, arcu nec dapibus efficitur, eros nisl mollis nibh, semper congue nunc ligula quis purus. Vestibulum in rutrum nibh. Nunc a lectus a eros tristique pharetra et quis velit. Maecenas efficitur justo quis magna porta, ut dapibus enim dapibus. Phasellus vitae turpis at dolor tincidunt cursus non non diam. Mauris accumsan quam mauris, quis aliquam turpis condimentum ac. Quisque sollicitudin cursus sem. Sed ultrices accumsan ipsum, sed auctor ex.\r\n\r\nPraesent dapibus arcu vel metus mollis, ut pulvinar purus sagittis. Curabitur mi ligula, luctus at hendrerit in, vestibulum a orci. Proin lorem orci, sagittis vel molestie sit amet, fermentum in libero. Donec id tortor augue. Aenean vulputate vel nibh sit amet pellentesque. Nam laoreet sodales porttitor. Pellentesque condimentum est ut eros tempus pretium. Vestibulum eget consequat orci. Curabitur pellentesque sem eget neque ullamcorper, vel condimentum neque pellentesque. Nullam fringilla feugiat finibus. Fusce posuere est nec leo viverra sollicitudin. Proin facilisis nibh in ante dignissim volutpat. Donec vitae leo ultricies, suscipit turpis vitae, ultricies augue. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.\r\n\r\nProin efficitur sed lectus et sagittis. Aliquam se",
              ),
              mapOf(
                "creationDateTime" to "2024-01-30T15:00:13.644075",
                "authorName" to "David Middleton",
                "additionalNoteText" to "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc bibendum sapien et odio suscipit, vitae scelerisque velit vehicula. Nulla mauris purus, semper eu ipsum vel, sollicitudin fringilla est. Ut dui turpis, malesuada non blandit vel, imperdiet ac libero. Nullam aliquam interdum augue nec placerat. Duis euismod, arcu nec dapibus efficitur, eros nisl mollis nibh, semper congue nunc ligula quis purus. Vestibulum in rutrum nibh. Nunc a lectus a eros tristique pharetra et quis velit. Maecenas efficitur justo quis magna porta, ut dapibus enim dapibus. Phasellus vitae turpis at dolor tincidunt cursus non non diam. Mauris accumsan quam mauris, quis aliquam turpis condimentum ac. Quisque sollicitudin cursus sem. Sed ultrices accumsan ipsum, sed auctor ex.\r\n\r\nPraesent dapibus arcu vel metus mollis, ut pulvinar purus sagittis. Curabitur mi ligula, luctus at hendrerit in, vestibulum a orci. Proin lorem orci, sagittis vel molestie sit amet, fermentum in libero. Donec id tortor augue. Aenean vulputate vel nibh sit amet pellentesque. Nam laoreet sodales porttitor. Pellentesque condimentum est ut eros tempus pretium. Vestibulum eget consequat orci. Curabitur pellentesque sem eget neque ullamcorper, vel condimentum neque pellentesque. Nullam fringilla feugiat finibus. Fusce posuere est nec leo viverra sollicitudin. Proin facilisis nibh in ante dignissim volutpat. Donec vitae leo ultricies, suscipit turpis vitae, ultricies augue. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.\r\n\r\nProin efficitur sed lectus et sagittis. Aliquam semper nibh quis molestie cursus. Donec finibus velit ut dictum pretium. Maecenas bibendum odio nec ex sodales, at dapibus dui maximus. Nam ullamcorper est et risus ullamcorper, eu consequat lacus posuere. Cras sit amet est ultrices, maximus neque et, luctus magna. Praesent dignissim augue dignissim, dictum massa id, gravida sapien. Nunc a dolor rutrum, porttitor felis eu, suscipit sapien. Donec semper leo a egestas tempor. Duis non ultricies ex. Fusce orci libero, dignissim at diam pellentesque, pulvinar viverra sem. Integer vel eleifend tortor, ut sagittis lorem. Vivamus rhoncus varius sem, eu sollicitudin nibh laoreet a",
              ),
              mapOf(
                "creationDateTime" to "2024-01-30T15:01:21.907679",
                "authorName" to "David Middleton",
                "additionalNoteText" to "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc bibendum sapien et odio suscipit, vitae scelerisque velit vehicula. Nulla mauris purus, semper eu ipsum vel, sollicitudin fringilla est. Ut dui turpis, malesuada non blandit vel, imperdiet ac libero. Nullam aliquam interdum augue nec placerat. Duis euismod, arcu nec dapibus efficitur, eros nisl mollis nibh, semper congue nunc ligula quis purus. Vestibulum in rutrum nibh. Nunc a lectus a eros tristique pharetra et quis velit. Maecenas efficitur justo quis magna porta, ut dapibus enim dapibus. Phasellus vitae turpis at dolor tincidunt cursus non non diam. Mauris accumsan quam mauris, quis aliquam turpis condimentum ac. Quisque sollicitudin cursus sem. Sed ultrices accumsan ipsum, sed auctor ex.\r\n\r\nPraesent dapibus arcu vel metus mollis, ut pulvinar purus sagittis. Curabitur mi ligula, luctus at hendrerit in, vestibulum a orci. Proin lorem orci, sagittis vel molestie sit amet, fermentum in libero. Donec id tortor augue. Aenean vulputate vel nibh sit amet pellentesque. Nam laoreet sodales porttitor. Pellentesque condimentum est ut eros tempus pretium. Vestibulum eget consequat orci. Curabitur pellentesque sem eget neque ullamcorper, vel condimentum neque pellentesque. Nullam fringilla feugiat finibus. Fusce posuere est nec leo viverra sollicitudin. Proin facilisis nibh in ante dignissim volutpat. Donec vitae leo ultricies, suscipit turpis vitae, ultricies augue. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.\r\n\r\nProin efficitur sed lectus et sagittis. Aliquam semper nibh quis molestie cursus. Donec finibus velit ut dictum pretium. Maecenas bibendum odio nec ex sodales, at dapibus dui maximus. Nam ullamcorper est et risus ullamcorper, eu consequat lacus posuere. Cras sit amet est ultrices, maximus neque et, luctus magna. Praesent dignissim augue dignissim, dictum massa id, gravida sapien. Nunc a dolor rutrum, porttitor felis eu, suscipit sapien. Donec semper leo a egestas tempor. Duis non ultricies ex. Fusce orci libero, dignissim at diam pellentesque, pulvinar viverra sem. Integer vel eleifend tortor, ut sagittis lorem. Vivamus rhoncus varius sem, eu sollicitudin nibh laoreet a",
              ),
            ),
          ),
          mapOf(
            "type" to "STAR",
            "subType" to "WARS",
            "creationDateTime" to "2024-01-30T17:29:59.142356",
            "authorName" to "David Middleton",
            "text" to "NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1",
            "amendments" to arrayListOf(
              mapOf(
                "creationDateTime" to "2024-02-27T11:57:18.083934",
                "authorName" to "David Middleton",
                "additionalNoteText" to "NEW",
              ),
            ),
          ),
          mapOf(
            "type" to "SA",
            "subType" to "SARTH",
            "creationDateTime" to "27 February 2024, 11:57:18am",
            "authorName" to "",
            "amendments" to arrayListOf<Any>(),
          ),
        )
        val testResponseObject = listOf(DpsService(name = "offender-case-notes", content = testServiceData))
        val writer = PdfWriter(FileOutputStream("dummy-template.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy-template.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("Case notes")
      }

      it("converts data to YAML format in the event of no template") {
        val testInput = mapOf(
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
        val testResponseObject = listOf(DpsService(name = "fake-service-name", content = testInput))
        val writer = PdfWriter(FileOutputStream("dummy.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)

        Assertions.assertThat(text).contains("Fake service name")
        Assertions.assertThat(text).contains("Test date text: \"Test\"")
        Assertions.assertThat(text).contains("Test data number: 99")
        Assertions.assertThat(text).contains("Test data array: \n  - 1 \n  - 2 \n  - 3 \n  - 4 \n  - 5 ")
        Assertions.assertThat(text).contains("Test data map: \n  A: \"1\" \n  B: \"2\" ")
        Assertions.assertThat(text).contains(
          "Test data nested: \n" +
            "  A: \"test\" \n" +
            "  B: 2 \n" +
            "  C: \n    - \"alpha\" \n    - \"beta\" \n    - \"gamma\" \n    - \"delta\" \n" +
            "  D: \n    X: 1 \n    Z: 2 ",
        )
        Assertions.assertThat(text).contains(
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

      it("creates a full PDF report") {
        val testInput = mapOf(
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
        val testContentObject = listOf(DpsService(name = "fake-service-name", content = testInput))

        // PDF set up
        val fullDocumentWriter = PdfWriter(FileOutputStream("dummy.pdf"))
        val mainPdfStream = ByteArrayOutputStream()
        val mockPdfDocument = PdfDocument(PdfWriter(mainPdfStream))
        val mockDocument = Document(mockPdfDocument)

        // Add content and rear pages
        val testDataFromServices = listOf(
          DpsService(
            name = "fake-service-name-1",
            content = mapOf("fake-prisoner-search-property-eg-age" to "dummy age", "fake-prisoner-search-property-eg-name" to "dummy name"),
          ),
          DpsService(
            name = "fake-service-name-2",
            content = mapOf("fake-prisoner-search-property-eg-age" to "dummy age", "fake-prisoner-search-property-eg-name" to "dummy name"),
          ),
        )
        generatePdfService.addInternalContentsPage(pdfDocument = mockPdfDocument, document = mockDocument, services = testDataFromServices)
        generatePdfService.addExternalCoverPage(pdfDocument = mockPdfDocument, document = mockDocument, subjectName = "LASTNAME, Firstname", nomisId = "mockNomisNumber", ndeliusCaseReferenceId = null, sarCaseReferenceNumber = "mockCaseReference", dateFrom = LocalDate.now(), dateTo = LocalDate.now())
        mockPdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, CustomHeaderEventHandler(mockPdfDocument, mockDocument, "NOMIS ID: mockNomisNumber", "LASTNAME, Firstname"))
        generatePdfService.addData(mockPdfDocument, mockDocument, testContentObject)
        val numPages = mockPdfDocument.numberOfPages
        generatePdfService.addRearPage(mockPdfDocument, mockDocument, numPages)
        mockDocument.close()

        // Add cover page
        val coverPdfStream = ByteArrayOutputStream()
        val coverPage = PdfDocument(PdfWriter(coverPdfStream))
        val coverPageDocument = Document(coverPage)
        generatePdfService.addInternalCoverPage(
          document = coverPageDocument,
          subjectName = "LASTNAME, Firstname",
          nomisId = "mockNomisNumber",
          ndeliusCaseReferenceId = null,
          sarCaseReferenceNumber = "mockCaseReference",
          dateFrom = LocalDate.now(),
          dateTo = LocalDate.now(),
          services = testDataFromServices,
          numPages = numPages,
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
        Assertions.assertThat(fullDocument.numberOfPages).isEqualTo(5)
        fullDocument.close()
        val reader = PdfDocument(PdfReader("dummy.pdf"))

        val coverpage = reader.getPage(1)
        val coverpageText = PdfTextExtractor.getTextFromPage(coverpage)
        val dataPage = reader.getPage(4)
        val dataPageText = PdfTextExtractor.getTextFromPage(dataPage)
        val expectedHeaderSubjectName = "Name: LASTNAME, Firstname"
        val expectedHeaderSubjectId = "NOMIS ID: mockNomisNumber"

        Assertions.assertThat(coverpageText).contains("SUBJECT ACCESS REQUEST REPORT")
        Assertions.assertThat(coverpageText).contains("NOMIS ID: mockNomisNumber")
        Assertions.assertThat(dataPageText).contains(expectedHeaderSubjectName)
        Assertions.assertThat(dataPageText).contains(expectedHeaderSubjectId)
      }
    }

    describe("preProcessData") {
      it("processValue if input is a string/number/null") {
        Assertions.assertThat(generatePdfService.preProcessData("testInput")).isEqualTo("testInput")
        Assertions.assertThat(generatePdfService.preProcessData(5)).isEqualTo(5)
        Assertions.assertThat(generatePdfService.preProcessData(null)).isEqualTo("No data held") // - How does bodyToMono handle null?
      }

      it("preprocesses correctly for simple string object") {
        val testInput = mapOf("testKey" to "testValue")
        val testOutput = mapOf("Test key" to "testValue")

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("preprocesses correctly for a map of maps") {
        val testInput = mapOf("parentTestKey" to mapOf("nestedTestKey" to "nestedTestValue"))
        val testOutput = mapOf("Parent test key" to mapOf("Nested test key" to "nestedTestValue"))

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("preprocesses correctly for array of objects") {
        val testInput = arrayListOf(mapOf("testKeyOne" to "testValueOne"), mapOf("testKeyTwo" to "testValueTwo"))
        val testOutput = arrayListOf(mapOf("Test key one" to "testValueOne"), mapOf("Test key two" to "testValueTwo"))

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("preprocesses correctly for a map of arrays of maps of arrays") {
        val testInput = mapOf("parentTestKey" to arrayListOf(mapOf("nestedTestKey" to arrayListOf("testString"))))
        val testOutput = mapOf("Parent test key" to arrayListOf(mapOf("Nested test key" to arrayListOf("testString"))))

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("replaces null values in a simple string object") {
        val testInput = mapOf("testKey" to null)
        val testOutput = mapOf("Test key" to "No data held")

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("replaces null values in a simple string object") {
        val testInput = mapOf("testKey" to "null")
        val testOutput = mapOf("Test key" to "No data held")

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("replaces null values in a map of maps") {
        val testInput = mapOf("parentTestKey" to mapOf("nestedTestKey" to "null"))
        val testOutput = mapOf("Parent test key" to mapOf("Nested test key" to "No data held"))

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("replaces null values in arrays of maps") {
        val testInput = arrayListOf(mapOf("testKeyOne" to "testValueOne"), mapOf("testKeyTwo" to "null"))
        val testOutput = arrayListOf(mapOf("Test key one" to "testValueOne"), mapOf("Test key two" to "No data held"))

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("replaces empty array lists") {
        val testInput = arrayListOf<Any?>()
        val testOutput = "No data held"

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("replaces null values and arrays in a map of arrays of maps of arrays") {
        val testInput = mapOf("parentTestKey" to arrayListOf<Any?>())
        val testOutput = mapOf("Parent test key" to "No data held")

        Assertions.assertThat(generatePdfService.preProcessData(testInput)).isEqualTo(testOutput)
      }

      it("processValue replaces dates in various formats") {
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
            "input" to "01/05/2024 17:43",
            "expected" to "01 May 2024, 5:43 pm",
          ),
          mapOf(
            "input" to "01/05/2024 17:43:59",
            "expected" to "01 May 2024, 5:43:59 pm",
          ),
        )

        testCases.forEach { test ->
          Assertions.assertThat(generatePdfService.processValue(test["input"])).isEqualTo(test["expected"])
        }
      }
    }
  },
)
