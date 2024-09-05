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
        val testResponseObject: List<DpsService> = listOf(
          DpsService(
            name = "test-service",
            content = mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()),
          ),
        )
        Mockito.mock(Document::class.java)
        Mockito.mock(ByteArrayOutputStream::class.java)

        val stream = generatePdfService.execute(
          testResponseObject,
          "EGnomisID",
          "EGnDeliusID",
          "EGsarID",
          "",
          LocalDate.of(1999, 12, 30),
          LocalDate.of(2010, 12, 30),
        )

        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("returns the same stream") {
        val testResponseObject: List<DpsService> = listOf(
          DpsService(
            name = "test-service",
            content = mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()),
          ),
        )
        val mockStream = Mockito.mock(ByteArrayOutputStream::class.java)

        val result = generatePdfService.execute(
          testResponseObject,
          "",
          "",
          "",
          "",
          LocalDate.of(1999, 12, 30),
          LocalDate.of(2010, 12, 30),
          mockStream,
        )

        Assertions.assertThat(result).isEqualTo(mockStream)
      }

      it("handles no data being extracted") {
        val testResponseObject = listOf(DpsService(name = "test-service", content = null))
        Mockito.mock(Document::class.java)
        Mockito.mock(ByteArrayOutputStream::class.java)
        Assertions.assertThat(testResponseObject[0].content).isNull()

        val stream = generatePdfService.execute(
          testResponseObject,
          "",
          "",
          "",
          "",
          LocalDate.of(1999, 12, 30),
          LocalDate.of(2010, 12, 30),
        )

        Assertions.assertThat(stream).isInstanceOf(ByteArrayOutputStream::class.java)
      }

      it("adds rear page with correct text") {
        val writer = PdfWriter(FileOutputStream("dummy.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)

        mockDocument.add(Paragraph("This page represents the upstream data pages"))
        val numberOfPagesWithoutRearAndCoverPage = mockPdfDocument.numberOfPages
        mockDocument.add(AreaBreak(AreaBreakType.NEXT_PAGE))
          .add(Paragraph("This page represents the internal cover page"))
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
          generatePdfService.addExternalCoverPage(
            mockPdfDocument,
            mockDocument,
            "LASTNAME, FIRSTNAME",
            "mockNomisNumber",
            null,
            "mockCaseReference",
            LocalDate.now(),
            LocalDate.now(),
          )
          mockDocument.close()

          val reader = PdfDocument(PdfReader("dummy.pdf"))
          val page = reader.getPage(2)
          val text = PdfTextExtractor.getTextFromPage(page)

          Assertions.assertThat(text).contains("SUBJECT ACCESS REQUEST REPORT")
          Assertions.assertThat(text).contains("NOMIS ID: mockNomisNumber")
          Assertions.assertThat(text).contains("Name: LASTNAME, FIRSTNAME")
        }
      }

      it("renders for Case Notes Service") {
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
        Assertions.assertThat(text).contains("Case note")
      }

      it("renders for Complexity of Need Service") {
        val testInput = arrayListOf(
          mapOf(
            "offenderNo" to "A1234AA",
            "level" to "low",
            "sourceSystem" to "keyworker-to-complexity-api-test",
            "sourceUser" to "JSMITH_GEN",
            "notes" to "string",
            "createdTimeStamp" to "2021-03-30T11:45:10.266Z",
            "active" to true,
          ),
          mapOf(
            "offenderNo" to "A1234AA",
            "level" to "low",
            "sourceSystem" to "keyworker-to-complexity-api-test",
            "sourceUser" to "JSMITH_GEN",
            "notes" to "string",
            "createdTimeStamp" to "2021-03-30T19:54:46.056Z",
            "active" to true,
          ),
        )
        val testResponseObject = listOf(DpsService(name = "hmpps-complexity-of-need", content = testInput))
        val writer = PdfWriter(FileOutputStream("dummy-template-con.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy-template-con.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("Complexity of need")
      }

      it("renders for Keyworker Service") {
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "offenderKeyworkerId" to 12912,
            "offenderNo" to "A1234AA",
            "staffId" to 485634,
            "assignedDateTime" to "2019-12-03T11:00:58.21264",
            "active" to false,
            "allocationReason" to "MANUAL",
            "allocationType" to "M",
            "userId" to "JROBERTSON_GEN",
            "prisonId" to "MDI",
            "expiryDateTime" to "2020-12-02T16:31:01",
            "deallocationReason" to "RELEASED",
            "creationDateTime" to "2019-12-03T11:00:58.213527",
            "createUserId" to "JROBERTSON_GEN",
            "modifyDateTime" to "2020-12-02T16:31:32.128317",
            "modifyUserId" to "JROBERTSON_GEN",
          ),
        )
        val testResponseObject = listOf(DpsService(name = "keyworker-api", content = testServiceData))
        val writer = PdfWriter(FileOutputStream("dummy-keyworker-template.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy-keyworker-template.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("Keyworker")
      }

      it("renders for Create and Vary a License Service") {

        val testInput = mapOf(
          "licences" to arrayListOf(
            mapOf(
              "kind" to "VARIATION",
              "id" to 157,
              "typeCode" to "AP",
              "version" to "2.1",
              "statusCode" to "ACTIVE",
              "nomsId" to "A8272DY",
              "bookingId" to 1201812,
              "appointmentPerson" to "Test",
              "appointmentTime" to null,
              "appointmentTimeType" to "IMMEDIATE_UPON_RELEASE",
              "appointmentAddress" to "Test, , Test, Test, TEST",
              "appointmentContact" to "00000000000",
              "approvedDate" to "26/07/2024 14:49:51",
              "approvedByUsername" to "CVL_ACO",
              "submittedDate" to "26/07/2024 14:47:02",
              "approvedByName" to "Tim Harrison",
              "supersededDate" to null,
              "dateCreated" to "26/07/2024 14:44:07",
              "createdByUsername" to "CVL_COM",
              "dateLastUpdated" to "26/07/2024 14:50:09",
              "updatedByUsername" to "CVL_COM",
              "standardLicenceConditions" to arrayListOf(
                mapOf(
                  "code" to "9ce9d594-e346-4785-9642-c87e764bee37",
                  "text" to "Be of good behaviour and not behave in a way which undermines the purpose of the licence period.",
                ),
                mapOf(
                  "code" to "3b19fdb0-4ca3-4615-9fdd-61fabc1587af",
                  "text" to "Not commit any offence.",
                ),
                mapOf(
                  "code" to "3361683a-504a-4357-ae22-6aa01b370b4a",
                  "text" to "Keep in touch with the supervising officer in accordance with instructions given by the supervising officer.",
                ),
                mapOf(
                  "code" to "9fc04065-df29-4bda-9b1d-bced8335c356",
                  "text" to "Receive visits from the supervising officer in accordance with any instructions given by the supervising officer.",
                ),
                mapOf(
                  "code" to "e670ac69-eda2-4b04-a0a1-a3c8492fe1e6",
                  "text" to "Reside permanently at an address approved by the supervising officer and obtain the prior permission of the supervising officer for any stay of one or more nights at a different address.",
                ),
                mapOf(
                  "code" to "78A5F860-4791-48F2-B707-D6D4413850EE",
                  "text" to "Tell the supervising officer if you use a name which is different to the name or names which appear on your licence.",
                ),
                mapOf(
                  "code" to "6FA6E492-F0AB-4E76-B868-63813DB44696",
                  "text" to "Tell the supervising officer if you change or add any contact details, including phone number or email.",
                ),
                mapOf(
                  "code" to "88069445-08cb-4f16-915f-5a162d085c26",
                  "text" to "Not undertake work, or a particular type of work, unless it is approved by the supervising officer and notify the supervising officer in advance of any proposal to undertake work or a particular type of work.",
                ),
                mapOf(
                  "code" to "7d416906-0e94-4fde-ae86-8339d339ccb7",
                  "text" to "Not travel outside the United Kingdom, the Channel Islands or the Isle of Man except with the prior permission of the supervising officer or for the purposes of immigration deportation or removal.",
                ),
              ),
              "standardPssConditions" to null,
              "additionalLicenceConditions" to arrayListOf(
                mapOf(
                  "code" to "5db26ab3-9b6f-4bee-b2aa-53aa3f3be7dd",
                  "version" to "2.1",
                  "category" to "Residence at a specific place",
                  "expandedText" to "You must reside overnight within West Midlands probation region while of no fixed abode, unless otherwise approved by your supervising officer.",
                  "data" to arrayListOf(
                    mapOf(
                      "field" to "probationRegion",
                      "value" to "West Midlands",
                    ),
                  ),
                  "uploadSummary" to null,
                  "readyToSubmit" to true,
                ),
                mapOf(
                  "code" to "b72fdbf2-0dc9-4e7f-81e4-c2ccb5d1bc90",
                  "version" to "2.1",
                  "category" to "Contact with a person",
                  "expandedText" to "Attend all appointments arranged for you with a psychiatrist / psychologist / medical practitioner, unless otherwise approved by your supervising officer.",
                  "data" to null,
                  "uploadSummary" to null,
                  "readyToSubmit" to true,
                ),
              ),
              "additionalPssConditions" to null,
              "bespokeConditions" to arrayListOf(
                mapOf(
                  "text" to "test",
                ),
              ),
              "createdByFullName" to "CVL COM",
              "licenceVersion" to "2.0",
            ),
          ),
        )

        val testResponseObject = listOf(DpsService(name = "create-and-vary-a-licence-api", content = testInput))
        val writer = PdfWriter(FileOutputStream("dummy-template.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy-template.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("Create and vary a licence")
      }

      it("renders for Home Detention Curfew Service") {
        val testServiceData: Map<Any, Any> = mapOf(
          "licences" to arrayListOf(
            mapOf(
              "id" to 1626,
              "prisonNumber" to "G1556UH",
              "bookingId" to 1108337,
              "stage" to "PROCESSING_RO",
              "version" to 1,
              "transitionDate" to "2024-03-18T09:24:35.473079",
              "varyVersion" to 0,
              "additionalConditionsVersion" to null,
              "standardConditionsVersion" to null,
              "deletedAt" to "2024-03-18T09:25:06.780003",
              "licence" to mapOf(
                "eligibility" to mapOf(
                  "crdTime" to mapOf(
                    "decision" to "No",
                  ),
                  "excluded" to mapOf(
                    "decision" to "No",
                  ),
                  "suitability" to mapOf(
                    "decision" to "No",
                  ),
                ),
                "bassReferral" to mapOf(
                  "bassRequest" to mapOf(
                    "specificArea" to "No",
                    "bassRequested" to "Yes",
                    "additionalInformation" to "",
                  ),
                ),
                "proposedAddress" to mapOf(
                  "optOut" to mapOf(
                    "decision" to "No",
                  ),
                ),
                "addressProposed" to mapOf(
                  "decision" to "No",
                ),
              ),
            ),
          ),
          "licenceVersions" to arrayListOf(
            mapOf(
              "id" to 446,
              "prisonNumber" to "G1556UH",
              "bookingId" to 1108337,
              "timestamp" to "2024-03-15T10:48:50.888663",
              "version" to 1,
              "template" to "hdc_ap",
              "varyVersion" to 0,
              "deletedAt" to "2024-03-15T11:11:14.361319",
              "licence" to mapOf(
                "risk" to mapOf(
                  "riskManagement" to mapOf(
                    "version" to "3",
                    "emsInformation" to "No",
                    "pomConsultation" to "Yes",
                    "mentalHealthPlan" to "No",
                    "unsuitableReason" to "",
                    "hasConsideredChecks" to "Yes",
                    "manageInTheCommunity" to "Yes",
                    "emsInformationDetails" to "",
                    "riskManagementDetails" to "",
                    "proposedAddressSuitable" to "Yes",
                    "awaitingOtherInformation" to "No",
                    "nonDisclosableInformation" to "No",
                    "nonDisclosableInformationDetails" to "",
                    "manageInTheCommunityNotPossibleReason" to "",
                  ),
                ),
                "curfew" to mapOf(
                  "firstNight" to mapOf(
                    "firstNightFrom" to "15:00",
                    "firstNightUntil" to "07:00",
                  ),
                ),
                "curfewHours" to mapOf(
                  "allFrom" to "19:00",
                  "allUntil" to "07:00",
                  "fridayFrom" to "19:00",
                  "mondayFrom" to "19:00",
                  "sundayFrom" to "19:00",
                  "fridayUntil" to "07:00",
                  "mondayUntil" to "07:00",
                  "sundayUntil" to "07:00",
                  "tuesdayFrom" to "19:00",
                  "saturdayFrom" to "19:00",
                  "thursdayFrom" to "19:00",
                  "tuesdayUntil" to "07:00",
                  "saturdayUntil" to "07:00",
                  "thursdayUntil" to "07:00",
                  "wednesdayFrom" to "19:00",
                  "wednesdayUntil" to "07:00",
                  "daySpecificInputs" to "No",
                ),
                "victim" to mapOf(
                  "victimLiaison" to mapOf(
                    "decision" to "No",
                  ),
                ),
                "approval" to mapOf(
                  "release" to mapOf(
                    "decision" to "Yes",
                    "decisionMaker" to "Louise Norris",
                    "reasonForDecision" to "",
                  ),
                ),
                "consideration" to mapOf(
                  "decision" to "Yes",
                ),
                "document" to mapOf(
                  "template" to mapOf(
                    "decision" to "hdc_ap",
                    "offenceCommittedBeforeFeb2015" to "No",
                  ),
                ),
                "reporting" to mapOf(
                  "reportingInstructions" to mapOf(
                    "name" to "sam",
                    "postcode" to "S3 8RD",
                    "telephone" to "47450",
                    "townOrCity" to "Sheffield",
                    "organisation" to "crc",
                    "reportingDate" to "12/12/2024",
                    "reportingTime" to "12:12",
                    "buildingAndStreet1" to "10",
                    "buildingAndStreet2" to "street",
                  ),
                ),
                "eligibility" to mapOf(
                  "crdTime" to mapOf(
                    "decision" to "No",
                  ),
                  "excluded" to mapOf(
                    "decision" to "No",
                  ),
                  "suitability" to mapOf(
                    "decision" to "No",
                  ),
                ),
                "finalChecks" to mapOf(
                  "onRemand" to mapOf(
                    "decision" to "No",
                  ),
                  "segregation" to mapOf(
                    "decision" to "No",
                  ),
                  "seriousOffence" to mapOf(
                    "decision" to "No",
                  ),
                  "confiscationOrder" to mapOf(
                    "decision" to "No",
                  ),
                  "undulyLenientSentence" to mapOf(
                    "decision" to "No",
                  ),
                ),
                "bassReferral" to mapOf(
                  "bassOffer" to mapOf(
                    "bassArea" to "Reading",
                    "postCode" to "RG1 6HM",
                    "telephone" to "",
                    "addressTown" to "Reading",
                    "addressLine1" to "The Street",
                    "addressLine2" to "",
                    "bassAccepted" to "Yes",
                    "bassOfferDetails" to "",
                  ),
                  "bassRequest" to mapOf(
                    "specificArea" to "No",
                    "bassRequested" to "Yes",
                    "additionalInformation" to "",
                  ),
                  "bassAreaCheck" to mapOf(
                    "bassAreaReason" to "",
                    "bassAreaCheckSeen" to "true",
                    "approvedPremisesRequiredYesNo" to "No",
                  ),
                ),
                "proposedAddress" to mapOf(
                  "optOut" to mapOf(
                    "decision" to "No",
                  ),
                  "addressProposed" to mapOf(
                    "decision" to "No",
                  ),
                ),
                "licenceConditions" to mapOf(
                  "standard" to mapOf(
                    "additionalConditionsRequired" to "No",
                  ),
                ),
              ),
            ),
          ),
          "auditEvents" to arrayListOf(
            mapOf(
              "id" to 40060,
              "timestamp" to "2024-08-23T09:36:51.186289",
              "user" to "cpxUKKZdbW",
              "action" to "UPDATE_SECTION",
              "details" to mapOf(
                "path" to "/hdc/curfew/approvedPremises/1108337",
                "bookingId" to "1108337",
                "userInput" to mapOf(
                  "required" to "Yes",
                ),
              ),
            ),
          ),
        )
        val testResponseObject = listOf(DpsService(name = "hmpps-hdc-api", content = testServiceData))
        val writer = PdfWriter(FileOutputStream("dummy-hdc-template.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy-hdc-template.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)
        Assertions.assertThat(text).contains("Home Detention Curfew")
      }

      it("renders for Use of Force Service") {

        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "id" to 190,
            "sequenceNo" to 2,
            "createdDate" to "2020-09-04T12:12:53.812536",
            "updatedDate" to "2021-03-30T11:31:16.854361",
            "incidentDate" to "2020-09-07T02:02:00",
            "submittedDate" to "2021-03-30T11:31:16.853",
            "deleted" to "2021-11-30T15:47:13.139",
            "status" to "SUBMITTED",
            "agencyId" to "MDI",
            "userId" to "ANDYLEE_ADM",
            "reporterName" to "Andrew Lee",
            "offenderNo" to "A1234AA",
            "bookingId" to 1048991,
            "formResponse" to mapOf(
              "evidence" to mapOf(
                "cctvRecording" to "YES",
                "baggedEvidence" to true,
                "bodyWornCamera" to "YES",
                "photographsTaken" to false,
                "evidenceTagAndDescription" to arrayListOf(
                  mapOf(
                    "description" to "sasasasas",
                    "evidenceTagReference" to "sasa",
                  ),
                  mapOf(
                    "description" to "sasasasas 2",
                    "evidenceTagReference" to "sasa 2",
                  ),
                ),
                "bodyWornCameraNumbers" to arrayListOf(
                  mapOf(
                    "cameraNum" to "sdsds",
                  ),
                  mapOf(
                    "cameraNum" to "xxxxx",
                  ),
                ),
              ),
              "involvedStaff" to arrayListOf(
                mapOf(
                  "name" to "Andrew Lee",
                  "email" to "andrew.lee@digital.justice.gov.uk",
                  "staffId" to 486084,
                  "username" to "ZANDYLEE_ADM",
                  "verified" to true,
                  "activeCaseLoadId" to "MDI",
                ),
                mapOf(
                  "name" to "Lee Andrew",
                  "email" to "lee.andrew@digital.justice.gov.uk",
                  "staffId" to 486084,
                  "username" to "AMD_LEE",
                  "verified" to true,
                  "activeCaseLoadId" to "MDI",
                ),
              ),
              "incidentDetails" to mapOf(
                "locationId" to 357591,
                "plannedUseOfForce" to false,
                "authorisedBy" to "",
                "witnesses" to arrayListOf(
                  mapOf(
                    "name" to "Andrew Lee",
                  ),
                  mapOf(
                    "name" to "Andrew Leedsd",
                  ),
                ),
              ),
              "useOfForceDetails" to mapOf(
                "bodyWornCamera" to "YES",
                "bodyWornCameraNumbers" to arrayListOf(
                  mapOf(
                    "cameraNum" to "sdsds",
                  ),
                  mapOf(
                    "cameraNum" to "sdsds 2",
                  ),
                ),
                "pavaDrawn" to false,
                "pavaDrawnAgainstPrisoner" to false,
                "pavaUsed" to false,
                "weaponsObserved" to "YES",
                "weaponTypes" to arrayListOf(
                  mapOf(
                    "weaponType" to "xxx",
                  ),
                  mapOf(
                    "weaponType" to "yyy",
                  ),
                ),
                "escortingHold" to false,
                "restraint" to true,
                "restraintPositions" to arrayListOf(
                  "ON_BACK",
                  "ON_FRONT",
                ),
                "batonDrawn" to false,
                "batonDrawnAgainstPrisoner" to false,
                "batonUsed" to false,
                "guidingHold" to false,
                "handcuffsApplied" to false,
                "positiveCommunication" to false,
                "painInducingTechniques" to false,
                "painInducingTechniquesUsed" to "NONE",
                "personalProtectionTechniques" to true,
              ),
              "reasonsForUseOfForce" to mapOf(
                "reasons" to arrayListOf(
                  "FIGHT_BETWEEN_PRISONERS",
                  "REFUSAL_TO_LOCATE_TO_CELL",
                ),
                "primaryReason" to "REFUSAL_TO_LOCATE_TO_CELL",
              ),
              "relocationAndInjuries" to mapOf(
                "relocationType" to "OTHER",
                "f213CompletedBy" to "adcdas",
                "prisonerInjuries" to false,
                "healthcareInvolved" to true,
                "healthcarePractionerName" to "dsffds",
                "prisonerRelocation" to "CELLULAR_VEHICLE",
                "relocationCompliancy" to false,
                "staffMedicalAttention" to true,
                "staffNeedingMedicalAttention" to arrayListOf(
                  mapOf(
                    "name" to "fdsfsdfs",
                    "hospitalisation" to false,
                  ),
                  mapOf(
                    "name" to "fdsfsdfs",
                    "hospitalisation" to false,
                  ),
                ),
                "prisonerHospitalisation" to false,
                "userSpecifiedRelocationType" to "fsf FGSDgf s gfsdgGG  gf ggrf",
              ),
            ),
            "statements" to arrayListOf(
              mapOf(
                "id" to 334,
                "reportId" to 280,
                "createdDate" to "2021-04-08T09:23:51.165439",
                "updatedDate" to "2021-04-21T10:09:25.626246",
                "submittedDate" to "2021-04-21T10:09:25.626246",
                "deleted" to "2021-04-21T10:09:25.626246",
                "nextReminderDate" to "2021-04-09T09:23:51.165",
                "overdueDate" to "2021-04-11T09:23:51.165",
                "removalRequestedDate" to "2021-04-21T10:09:25.626246",
                "userId" to "ZANDYLEE_ADM",
                "name" to "Andrew Lee",
                "email" to "andrew.lee@digital.justice.gov.uk",
                "statementStatus" to "REMOVAL_REQUESTED",
                "lastTrainingMonth" to 1,
                "lastTrainingYear" to 2019,
                "jobStartYear" to 2019,
                "staffId" to 486084,
                "inProgress" to true,
                "removalRequestedReason" to "example",
                "statement" to "example",
                "statementAmendments" to arrayListOf(
                  mapOf(
                    "id" to 334,
                    "statementId" to 198,
                    "additionalComment" to "this is an additional comment",
                    "dateSubmitted" to "2020-10-01T13:08:37.25919",
                    "deleted" to "2022-10-01T13:08:37.25919",
                  ),
                  mapOf(
                    "id" to 335,
                    "statementId" to 199,
                    "additionalComment" to "this is an additional additional comment",
                    "dateSubmitted" to "2020-10-01T13:08:37.25919",
                    "deleted" to "2022-10-01T13:08:37.25919",
                  ),
                ),
              ),
              mapOf(
                "id" to 334,
                "reportId" to 280,
                "createdDate" to "2021-04-08T09:23:51.165439",
                "updatedDate" to "2021-04-21T10:09:25.626246",
                "submittedDate" to "2021-04-21T10:09:25.626246",
                "deleted" to "2021-04-21T10:09:25.626246",
                "nextReminderDate" to "2021-04-09T09:23:51.165",
                "overdueDate" to "2021-04-11T09:23:51.165",
                "removalRequestedDate" to "2021-04-21T10:09:25.626246",
                "userId" to "ZANDYLEE_ADM",
                "name" to "Andrew Lee",
                "email" to "andrew.lee@digital.justice.gov.uk",
                "statementStatus" to "REMOVAL_REQUESTED",
                "lastTrainingMonth" to 1,
                "lastTrainingYear" to 2019,
                "jobStartYear" to 2019,
                "staffId" to 486084,
                "inProgress" to true,
                "removalRequestedReason" to "example",
                "statement" to "example",
                "statementAmendments" to arrayListOf(
                  mapOf(
                    "id" to 334,
                    "statementId" to 198,
                    "additionalComment" to "this is an additional comment",
                    "dateSubmitted" to "2020-10-01T13:08:37.25919",
                    "deleted" to "2022-10-01T13:08:37.25919",
                  ),
                  mapOf(
                    "id" to 335,
                    "statementId" to 199,
                    "additionalComment" to "this is an additional additional comment",
                    "dateSubmitted" to "2020-10-01T13:08:37.25919",
                    "deleted" to "2022-10-01T13:08:37.25919",
                  ),
                ),
              ),
            ),
          ),
        )

        val testResponseObject = listOf(DpsService(name = "hmpps-uof-data-api", content = testServiceData))
        val writer = PdfWriter(FileOutputStream("dummy-uof-template.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy-uof-template.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)

        Assertions.assertThat(text).contains("Use of force")
      }

      it("renders for Incentives Service") {
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "id" to 2898970,
            "bookingId" to "1208204",
            "prisonerNumber" to "A485634",
            "nextReviewDate" to "2019-12-03",
            "levelCode" to "ENH",
            "prisonId" to "UAL",
            "locationId" to "M-16-15",
            "reviewTime" to "2023-07-03T21:14:25.059172",
            "reviewedBy" to "MDI",
            "commentText" to "comment",
            "current" to true,
            "reviewType" to "REVIEW",
          ),
          mapOf(
            "id" to 2898971,
            "bookingId" to "4028021",
            "prisonerNumber" to "A1234AA",
            "nextReviewDate" to "2020-12-03",
            "levelCode" to "ENH",
            "prisonId" to "UAL",
            "locationId" to "M-16-15",
            "reviewTime" to "2023-07-03T21:14:25.059172",
            "reviewedBy" to "MDI",
            "commentText" to "comment",
            "current" to true,
            "reviewType" to "REVIEW",
          ),
        )

        val testResponseObject = listOf(DpsService(name = "hmpps-incentives-api", content = testServiceData))
        val writer = PdfWriter(FileOutputStream("dummy-template.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy-template.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)

        Assertions.assertThat(text).contains("Incentives")
      }

      it("renders a template given an activities template") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "prisonerNumber" to "A4743DZ",
            "fromDate" to "1970-01-01",
            "toDate" to "2000-01-01",
            "allocations" to arrayListOf(
              mapOf(
                "allocationId" to 16,
                "prisonCode" to "LEI",
                "prisonerStatus" to "ENDED",
                "startDate" to "2023-07-21",
                "endDate" to "2023-07-21",
                "activityId" to 3,
                "activitySummary" to "QAtestingKitchenActivity",
                "payBand" to "Pay band 5",
                "createdDate" to "2023-07-20",
              ),
              mapOf(
                "allocationId" to 10,
                "prisonCode" to "LEI",
                "prisonerStatus" to "NEW",
                "startDate" to "2023-07-21",
                "endDate" to "2023-07-21",
                "activityId" to 4,
                "activitySummary" to "QAtestingKitchenActivity",
                "payBand" to "Pay band 5",
                "createdDate" to "2023-07-20",
              ),
            ),
            "attendanceSummary" to arrayListOf(
              mapOf(
                "attendanceReasonCode" to "ATTENDED",
                "count" to 12,
              ),
              mapOf(
                "attendanceReasonCode" to "CANCELLED",
                "count" to 8,
              ),
            ),
            "waitingListApplications" to arrayListOf(
              mapOf(
                "waitingListId" to 1,
                "prisonCode" to "LEI",
                "activitySummary" to "Summary",
                "applicationDate" to "2023-08-11",
                "originator" to "Prison staff",
                "status" to "APPROVED",
                "statusDate" to "2022-11-12",
                "comments" to null,
                "createdDate" to "2023-08-10",
              ),
              mapOf(
                "waitingListId" to 10,
                "prisonCode" to "LEI",
                "activitySummary" to "Summary",
                "applicationDate" to "2024-08-11",
                "originator" to "Prison staff",
                "status" to "APPROVED",
                "statusDate" to null,
                "comments" to null,
                "createdDate" to "2023-08-10",
              ),
            ),
            "appointments" to arrayListOf(
              mapOf(
                "appointmentId" to 18305,
                "prisonCode" to "LEI",
                "categoryCode" to "CAREER",
                "startDate" to "2023-08-11",
                "startTime" to "10:00",
                "endTime" to "11:00",
                "extraInformation" to "",
                "attended" to "Unmarked",
                "createdDate" to "2023-08-10",
              ),
              mapOf(
                "appointmentId" to 16340,
                "prisonCode" to "LEI",
                "categoryCode" to "CAREER",
                "startDate" to "2023-08-11",
                "startTime" to "10:00",
                "endTime" to "11:00",
                "extraInformation" to "",
                "attended" to "Unmarked",
                "createdDate" to "2023-08-10",
              ),
            ),
          ),
        )

        val testResponseObject = listOf(DpsService(name = "hmpps-activities-management-api", content = testServiceData))
        val writer = PdfWriter(FileOutputStream("dummy-activities-template.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy-activities-template.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)

        Assertions.assertThat(text).contains("Activities")
      }

      it("renders a template given an activities template with missing data") {
        val templateRenderService = TemplateRenderService()
        val testServiceData: ArrayList<Any> = arrayListOf(
          mapOf(
            "prisonerNumber" to "A4743DZ",
            "fromDate" to "1970-01-01",
            "toDate" to "2000-01-01",
            "attendanceSummary" to arrayListOf(
              mapOf(
                "attendanceReasonCode" to "ATTENDED",
                "count" to 12,
              ),
              mapOf(
                "attendanceReasonCode" to "CANCELLED",
                "count" to 8,
              ),
            ),
            "waitingListApplications" to arrayListOf(
              mapOf(
                "waitingListId" to 1,
                "prisonCode" to "LEI",
                "activitySummary" to "Summary",
                "applicationDate" to "2023-08-11",
                "originator" to "Prison staff",
                "status" to "APPROVED",
                "statusDate" to "2022-11-12",
                "comments" to null,
                "createdDate" to "2023-08-10",
              ),
              mapOf(
                "waitingListId" to 10,
                "prisonCode" to "LEI",
                "activitySummary" to "Summary",
                "applicationDate" to "2024-08-11",
                "originator" to "Prison staff",
                "status" to "APPROVED",
                "statusDate" to null,
                "comments" to null,
                "createdDate" to "2023-08-10",
              ),
            ),
            "appointments" to arrayListOf(
              mapOf(
                "appointmentId" to 18305,
                "prisonCode" to "LEI",
                "categoryCode" to "CAREER",
                "startDate" to "2023-08-11",
                "startTime" to "10:00",
                "endTime" to "11:00",
                "extraInformation" to "",
                "attended" to "Unmarked",
                "createdDate" to "2023-08-10",
              ),
              mapOf(
                "appointmentId" to 16340,
                "prisonCode" to "LEI",
                "categoryCode" to "CAREER",
                "startDate" to "2023-08-11",
                "startTime" to "10:00",
                "endTime" to "11:00",
                "extraInformation" to "",
                "attended" to "Unmarked",
                "createdDate" to "2023-08-10",
              ),
            ),
          ),
        )

        val testResponseObject = listOf(DpsService(name = "hmpps-activities-management-api", content = testServiceData))
        val writer = PdfWriter(FileOutputStream("dummy-activities-template-incomplete.pdf"))
        val mockPdfDocument = PdfDocument(writer)
        val mockDocument = Document(mockPdfDocument)
        generatePdfService.addData(mockPdfDocument, mockDocument, testResponseObject)
        mockDocument.close()
        val reader = PdfDocument(PdfReader("dummy-activities-template-incomplete.pdf"))
        val page = reader.getPage(2)
        val text = PdfTextExtractor.getTextFromPage(page)

        Assertions.assertThat(text).contains("Activities")
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
