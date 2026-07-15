package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.html2pdf.attach.impl.layout.HtmlPageBreak
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.IBlockElement
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.properties.AreaBreakType
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.createWritablePdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.events.SubjectAccessRequestHeaderAndFooterEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.newDocument
import java.io.InputStream
import java.nio.file.Path

class ITextServicePdfRenderer : ServicePdfRenderer {

  companion object {
    private val converterProperties: ConverterProperties = ConverterProperties()
  }

  override suspend fun generateServicePdf(
    pdfRenderRequest: PdfRenderRequest,
    servicePdfPath: Path,
    serviceHtml: InputStream,
  ) {
    createWritablePdfDocument(output = servicePdfPath).use { pdf ->
      newDocument(pdf).use { document ->
        pdf.addEventHandler(
          PdfDocumentEvent.END_PAGE,
          SubjectAccessRequestHeaderAndFooterEventHandler(
            document = document,
            subjectName = pdfRenderRequest.subjectName,
            nomisId = pdfRenderRequest.subjectAccessRequest.nomisId,
            ndeliusCaseReferenceId = pdfRenderRequest.subjectAccessRequest.ndeliusCaseReferenceId,
          ),
        )

        HtmlConverter.convertToElements(serviceHtml, converterProperties).forEach { element ->
          when (element) {
            is IBlockElement -> document.add(element)
            is Image -> document.add(element)
            is HtmlPageBreak -> document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
            else -> {
              throw SubjectAccessRequestException("Unsupported element type found ${element.javaClass}")
            }
          }
        }
      }
    }
  }
}
