package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils
import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.github.mustachejava.MustacheFactory
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.IBlockElement
import com.itextpdf.layout.element.Paragraph
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter

class Mustache {

  fun compile(file: String): Int {
    val mf: MustacheFactory = DefaultMustacheFactory()
    val m: Mustache? = mf.compile(file)
    println(m)
    return 0
  }

  fun execute(dataObject: Any): Int {
    val mf = DefaultMustacheFactory()
    val mustache = mf.compile(mf.getReader("template.mustache"), "hello")
    mustache.execute(PrintWriter("test.html"), dataObject).flush()
    return 0
  }

  fun convertToPdf(htmlFileName: String, pdfFileName: String): String {
    val writer = PdfWriter(FileOutputStream("dummy.pdf"))
    val pdfDocument = PdfDocument(writer)
    val document = Document(pdfDocument)
    document.add(Paragraph("This is an example title").setFontSize(16f).setBold())
    document.add(Paragraph(""))
    document.add(Paragraph("This is an example text").setFontSize(10f))
    document.add(Paragraph(""))
    val elements = HtmlConverter.convertToElements(FileInputStream(htmlFileName))
    for (element in elements) {
      document.add(element as IBlockElement)
    }
    document.close()
    return pdfFileName
  }
}

data class Item(val title: String, val createdOn: String, val text: String)
data class Keyworker(
  val offenderKeyworkerId: String?,
  val offenderNo: String?,
  val staffId: String?,
  val assignedDateTime: String?,
  val active: String?,
  val allocationReason: String?,
  val allocationType: String?,
  val userId: String?,
  val prisonId: String?,
  val expiryDateTime: String?,
  val deallocationReason: String?,
  val creationDateTime: String?,
  val createUserId: String?,
  val modifyDateTime: String?,
  val modifyUserId: String?,
)
