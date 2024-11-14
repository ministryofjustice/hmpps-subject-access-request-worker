package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.layout.Document
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.PrisonDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.UserDetailsRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.TemplateHelpers
import java.io.FileOutputStream

class GeneratePdfServiceOffenderCaseNotesTest {
  private val prisonDetailsRepository: PrisonDetailsRepository = mock()
  private val userDetailsRepository: UserDetailsRepository = mock()
  private val templateHelpers = TemplateHelpers(prisonDetailsRepository, userDetailsRepository)
  private val templateRenderService = TemplateRenderService(templateHelpers)
  private val telemetryClient: TelemetryClient = mock()
  private val generatePdfService = GeneratePdfService(templateRenderService, telemetryClient)

  @Test
  fun `generatePdfService renders for Offender Case Notes Service`() {
    val serviceList = listOf(DpsService(name = "offender-case-notes", content = offenderCaseNoteServiceData))
    val pdfDocument = PdfDocument(PdfWriter(FileOutputStream("dummy-template.pdf")))
    val document = Document(pdfDocument)
    generatePdfService.addData(pdfDocument, document, serviceList)
    document.close()
    val reader = PdfDocument(PdfReader("dummy-template.pdf"))
    val text = PdfTextExtractor.getTextFromPage(reader.getPage(2))

    assertThat(text).contains("Case note")
  }

  private val offenderCaseNoteServiceData: ArrayList<Any> = arrayListOf(
    mapOf(
      "type" to "OMIC",
      "subType" to "OPEN_COMM",
      "creationDateTime" to "2024-01-29T15:00:59.618572",
      "authorName" to "Andy User",
      "text" to "Testing\r\n\r\nTesting",
      "amendments" to arrayListOf(
        mapOf(
          "creationDateTime" to "2024-01-30T14:54:12.520707",
          "authorName" to "Andy User",
          "additionalNoteText" to "More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. More text added. \r\n\r\n\r\nMore text added. More text added. More text added. ",
        ),
        mapOf(
          "creationDateTime" to "2024-01-30T14:59:46.747803",
          "authorName" to "Andy User",
          "additionalNoteText" to "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc bibendum sapien et odio suscipit, vitae scelerisque velit vehicula. Nulla mauris purus, semper eu ipsum vel, sollicitudin fringilla est. Ut dui turpis, malesuada non blandit vel, imperdiet ac libero. Nullam aliquam interdum augue nec placerat. Duis euismod, arcu nec dapibus efficitur, eros nisl mollis nibh, semper congue nunc ligula quis purus. Vestibulum in rutrum nibh. Nunc a lectus a eros tristique pharetra et quis velit. Maecenas efficitur justo quis magna porta, ut dapibus enim dapibus. Phasellus vitae turpis at dolor tincidunt cursus non non diam. Mauris accumsan quam mauris, quis aliquam turpis condimentum ac. Quisque sollicitudin cursus sem. Sed ultrices accumsan ipsum, sed auctor ex.\r\n\r\nPraesent dapibus arcu vel metus mollis, ut pulvinar purus sagittis. Curabitur mi ligula, luctus at hendrerit in, vestibulum a orci. Proin lorem orci, sagittis vel molestie sit amet, fermentum in libero. Donec id tortor augue. Aenean vulputate vel nibh sit amet pellentesque. Nam laoreet sodales porttitor. Pellentesque condimentum est ut eros tempus pretium. Vestibulum eget consequat orci. Curabitur pellentesque sem eget neque ullamcorper, vel condimentum neque pellentesque. Nullam fringilla feugiat finibus. Fusce posuere est nec leo viverra sollicitudin. Proin facilisis nibh in ante dignissim volutpat. Donec vitae leo ultricies, suscipit turpis vitae, ultricies augue. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.\r\n\r\nProin efficitur sed lectus et sagittis. Aliquam semper nibh quis molestie cursus. Donec finibus velit ut dictum pretium. Maecenas bibendum odio nec ex sodales, at dapibus dui maximus. Nam ullamcorper est et risus ullamcorper, eu consequat lacus posuere. Cras sit amet est ultrices, maximus neque et, luctus magna. Praesent dignissim augue dignissim, dictum massa id, gravida sapien. Nunc a dolor rutrum, porttitor felis eu, suscipit sapien. Donec semper leo a egestas tempor. Duis non ultricies ex. Fusce orci libero, dignissim at diam pellentesque, pulvinar viverra sem. Integer vel eleifend tortor, ut sagittis lorem. Vivamus rhoncus varius sem, eu sollicitudin nibh laoreet a\r\nLorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc bibendum sapien et odio suscipit, vitae scelerisque velit vehicula. Nulla mauris purus, semper eu ipsum vel, sollicitudin fringilla est. Ut dui turpis, malesuada non blandit vel, imperdiet ac libero. Nullam aliquam interdum augue nec placerat. Duis euismod, arcu nec dapibus efficitur, eros nisl mollis nibh, semper congue nunc ligula quis purus. Vestibulum in rutrum nibh. Nunc a lectus a eros tristique pharetra et quis velit. Maecenas efficitur justo quis magna porta, ut dapibus enim dapibus. Phasellus vitae turpis at dolor tincidunt cursus non non diam. Mauris accumsan quam mauris, quis aliquam turpis condimentum ac. Quisque sollicitudin cursus sem. Sed ultrices accumsan ipsum, sed auctor ex.\r\n\r\nPraesent dapibus arcu vel metus mollis, ut pulvinar purus sagittis. Curabitur mi ligula, luctus at hendrerit in, vestibulum a orci. Proin lorem orci, sagittis vel molestie sit amet, fermentum in libero. Donec id tortor augue. Aenean vulputate vel nibh sit amet pellentesque. Nam laoreet sodales porttitor. Pellentesque condimentum est ut eros tempus pretium. Vestibulum eget consequat orci. Curabitur pellentesque sem eget neque ullamcorper, vel condimentum neque pellentesque. Nullam fringilla feugiat finibus. Fusce posuere est nec leo viverra sollicitudin. Proin facilisis nibh in ante dignissim volutpat. Donec vitae leo ultricies, suscipit turpis vitae, ultricies augue. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.\r\n\r\nProin efficitur sed lectus et sagittis. Aliquam se",
        ),
        mapOf(
          "creationDateTime" to "2024-01-30T15:00:13.644075",
          "authorName" to "Andy User",
          "additionalNoteText" to "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc bibendum sapien et odio suscipit, vitae scelerisque velit vehicula. Nulla mauris purus, semper eu ipsum vel, sollicitudin fringilla est. Ut dui turpis, malesuada non blandit vel, imperdiet ac libero. Nullam aliquam interdum augue nec placerat. Duis euismod, arcu nec dapibus efficitur, eros nisl mollis nibh, semper congue nunc ligula quis purus. Vestibulum in rutrum nibh. Nunc a lectus a eros tristique pharetra et quis velit. Maecenas efficitur justo quis magna porta, ut dapibus enim dapibus. Phasellus vitae turpis at dolor tincidunt cursus non non diam. Mauris accumsan quam mauris, quis aliquam turpis condimentum ac. Quisque sollicitudin cursus sem. Sed ultrices accumsan ipsum, sed auctor ex.\r\n\r\nPraesent dapibus arcu vel metus mollis, ut pulvinar purus sagittis. Curabitur mi ligula, luctus at hendrerit in, vestibulum a orci. Proin lorem orci, sagittis vel molestie sit amet, fermentum in libero. Donec id tortor augue. Aenean vulputate vel nibh sit amet pellentesque. Nam laoreet sodales porttitor. Pellentesque condimentum est ut eros tempus pretium. Vestibulum eget consequat orci. Curabitur pellentesque sem eget neque ullamcorper, vel condimentum neque pellentesque. Nullam fringilla feugiat finibus. Fusce posuere est nec leo viverra sollicitudin. Proin facilisis nibh in ante dignissim volutpat. Donec vitae leo ultricies, suscipit turpis vitae, ultricies augue. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.\r\n\r\nProin efficitur sed lectus et sagittis. Aliquam semper nibh quis molestie cursus. Donec finibus velit ut dictum pretium. Maecenas bibendum odio nec ex sodales, at dapibus dui maximus. Nam ullamcorper est et risus ullamcorper, eu consequat lacus posuere. Cras sit amet est ultrices, maximus neque et, luctus magna. Praesent dignissim augue dignissim, dictum massa id, gravida sapien. Nunc a dolor rutrum, porttitor felis eu, suscipit sapien. Donec semper leo a egestas tempor. Duis non ultricies ex. Fusce orci libero, dignissim at diam pellentesque, pulvinar viverra sem. Integer vel eleifend tortor, ut sagittis lorem. Vivamus rhoncus varius sem, eu sollicitudin nibh laoreet a",
        ),
        mapOf(
          "creationDateTime" to "2024-01-30T15:01:21.907679",
          "authorName" to "Andy User",
          "additionalNoteText" to "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc bibendum sapien et odio suscipit, vitae scelerisque velit vehicula. Nulla mauris purus, semper eu ipsum vel, sollicitudin fringilla est. Ut dui turpis, malesuada non blandit vel, imperdiet ac libero. Nullam aliquam interdum augue nec placerat. Duis euismod, arcu nec dapibus efficitur, eros nisl mollis nibh, semper congue nunc ligula quis purus. Vestibulum in rutrum nibh. Nunc a lectus a eros tristique pharetra et quis velit. Maecenas efficitur justo quis magna porta, ut dapibus enim dapibus. Phasellus vitae turpis at dolor tincidunt cursus non non diam. Mauris accumsan quam mauris, quis aliquam turpis condimentum ac. Quisque sollicitudin cursus sem. Sed ultrices accumsan ipsum, sed auctor ex.\r\n\r\nPraesent dapibus arcu vel metus mollis, ut pulvinar purus sagittis. Curabitur mi ligula, luctus at hendrerit in, vestibulum a orci. Proin lorem orci, sagittis vel molestie sit amet, fermentum in libero. Donec id tortor augue. Aenean vulputate vel nibh sit amet pellentesque. Nam laoreet sodales porttitor. Pellentesque condimentum est ut eros tempus pretium. Vestibulum eget consequat orci. Curabitur pellentesque sem eget neque ullamcorper, vel condimentum neque pellentesque. Nullam fringilla feugiat finibus. Fusce posuere est nec leo viverra sollicitudin. Proin facilisis nibh in ante dignissim volutpat. Donec vitae leo ultricies, suscipit turpis vitae, ultricies augue. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.\r\n\r\nProin efficitur sed lectus et sagittis. Aliquam semper nibh quis molestie cursus. Donec finibus velit ut dictum pretium. Maecenas bibendum odio nec ex sodales, at dapibus dui maximus. Nam ullamcorper est et risus ullamcorper, eu consequat lacus posuere. Cras sit amet est ultrices, maximus neque et, luctus magna. Praesent dignissim augue dignissim, dictum massa id, gravida sapien. Nunc a dolor rutrum, porttitor felis eu, suscipit sapien. Donec semper leo a egestas tempor. Duis non ultricies ex. Fusce orci libero, dignissim at diam pellentesque, pulvinar viverra sem. Integer vel eleifend tortor, ut sagittis lorem. Vivamus rhoncus varius sem, eu sollicitudin nibh laoreet a",
        ),
      ),
    ),
    mapOf(
      "type" to "STAR",
      "subType" to "WARS",
      "creationDateTime" to "2024-01-30T17:29:59.142356",
      "authorName" to "Andy User",
      "text" to "NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1NoteEntry1",
      "amendments" to arrayListOf(
        mapOf(
          "creationDateTime" to "2024-02-27T11:57:18.083934",
          "authorName" to "Andy User",
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
}
