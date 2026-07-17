package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.chunking

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
import java.io.FileOutputStream

class HtmlToPdfConsumer(
  val pdfRenderRequest: PdfRenderRequest,
  val serviceConfiguration: ServiceConfiguration,
) : HtmlConsumer {

  private companion object {
    private val log = LoggerFactory.getLogger(HtmlToPdfConsumer::class.java)
  }

  private val pdfDocument = PdfDocument(
    PdfWriter(
      FileOutputStream(pdfRenderRequest.serviceDataPdfPath(serviceConfiguration).toFile()),
    ),
  )

  private val document = Document(pdfDocument).apply {
    setMargins(50F, 35F, 70F, 35F)

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

  override fun consume(chunk: String) {
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
    log.info("post-process chunk {}", memoryUsage())
  }

  override fun close() {
    log.info("closing PDF document: {}", pdfRenderRequest.fullReportPdfPath.toUri())
    this.pdfDocument.close()
  }
}
