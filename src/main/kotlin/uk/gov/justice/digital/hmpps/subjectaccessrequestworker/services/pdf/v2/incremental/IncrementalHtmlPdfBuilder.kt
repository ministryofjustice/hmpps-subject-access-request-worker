package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.incremental

import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.html2pdf.attach.impl.layout.HtmlPageBreak
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.IBlockElement
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.properties.AreaBreakType
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.events.SubjectAccessRequestHeaderAndFooterEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.memoryUsage
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2.PdfRenderRequest
import java.io.Closeable
import java.io.FileOutputStream

const val PDF_TOP_MARGIN = 50f
const val PDF_RIGHT_MARGIN = 35f
const val PDF_BOTTOM_MARGIN = 70f
const val PDF_LEFT_MARGIN = 35f

class IncrementalHtmlPdfBuilder(
  private val pdfRenderRequest: PdfRenderRequest,
  private val serviceConfiguration: ServiceConfiguration,
) : Closeable {

  private companion object {
    private val log = LoggerFactory.getLogger(IncrementalHtmlPdfBuilder::class.java)
  }

  private val pdfDocument = PdfDocument(
    PdfWriter(
      FileOutputStream(pdfRenderRequest.serviceDataPdfPath(serviceConfiguration).toFile()),
    ),
  )

  private val document = Document(pdfDocument).apply {
    setMargins(PDF_TOP_MARGIN, PDF_RIGHT_MARGIN, PDF_BOTTOM_MARGIN, PDF_LEFT_MARGIN)

    pdfDocument.addEventHandler(
      PdfDocumentEvent.END_PAGE,
      SubjectAccessRequestHeaderAndFooterEventHandler(
        document = this,
        subjectName = pdfRenderRequest.subjectName,
        nomisId = pdfRenderRequest.subjectAccessRequest.nomisId,
        ndeliusCaseReferenceId = pdfRenderRequest.subjectAccessRequest.ndeliusCaseReferenceId,
      ),
    )
  }

  fun append(chunk: String) {
    log.info("pre-process chunk {}", memoryUsage())

    HtmlConverter.convertToElements(chunk).forEach { element ->
      when (element) {
        is IBlockElement -> document.add(element)
        is Image -> document.add(element)
        is HtmlPageBreak -> document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        else -> {
          throw SubjectAccessRequestException("Unsupported element type found ${element.javaClass}")
        }
      }
    }

    document.flush()
    log.info("post-process chunk, file:{}, {}", pdfRenderRequest.fullReportPdfPath.toUri(), memoryUsage())
  }

  override fun close() {
    log.info("closing PDF document: {}", pdfRenderRequest.fullReportPdfPath.toUri())
    this.document.close()
  }
}