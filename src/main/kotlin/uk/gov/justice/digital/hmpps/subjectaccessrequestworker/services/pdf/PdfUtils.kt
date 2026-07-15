package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Path

/**
 * Creates a new writeable PDF Document.
 *
 * @param output - The location where the PDF Document will be created.
 * @return A new [com.itextpdf.kernel.pdf.PdfDocument] that can be written to. The caller is responsible for ensuring
 * the PDF is closed when finished.
 */
fun createWritablePdfDocument(
  output: Path,
): PdfDocument = PdfDocument(PdfWriter(FileOutputStream(output.toFile()))).apply {
  isFlushUnusedObjects = true
}

/**
 * Creates a new readable PDF Document from the specified source.
 *
 * @param src The source of the PDF file to create the PDF Document from.
 * @return A new read-only [com.itextpdf.kernel.pdf.PdfDocument] from the source provided. The caller is responsible for
 * ensuring the PDF is closed when finished.
 */
fun getReadablePdfDocument(src: InputStream) = PdfDocument(PdfReader(src))

/**
 * Creates a new InputStream for the specified file.
 *
 * @param path The Path of the file to create the InputStream for.
 * @return an [InputStream] for the specified file.
 */
fun getInputStream(path: Path): InputStream = FileInputStream(path.toFile())

/**
 * Creates a new [Document] with common configuration values set for Subject Access Request reports.
 *
 * @param pdf The [PdfDocument] to create the Document from.
 * @return a new [Document] constructed from the specified [PdfDocument]. The caller is responsible for ensuring the
 * Document is closed when finished.
 */
fun newDocument(
  pdf: PdfDocument,
): Document = Document(pdf, pdf.defaultPageSize, true).apply {
  setMargins(50F, 35F, 70F, 35F)
}

fun memoryUsage(): String {
  val runtime = Runtime.getRuntime()
  val memoryUsed = ((runtime.totalMemory() - runtime.freeMemory()) / 1024) / 1024
  val totalMemory = (runtime.totalMemory() / 1024) / 1024
  val maxMemory = (runtime.maxMemory() / 1024) / 1024
  return "Memory used: ${memoryUsed}MB, Total memory: ${totalMemory}MB, Max memory: ${maxMemory}MB"
}
