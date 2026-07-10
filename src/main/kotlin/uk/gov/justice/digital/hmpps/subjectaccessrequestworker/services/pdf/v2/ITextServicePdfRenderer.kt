package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.v2

import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.html2pdf.attach.impl.layout.HtmlPageBreak
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.IBlockElement
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.properties.AreaBreakType
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.createWritablePdfDocument
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.pdf.newDocument
import java.io.InputStream
import java.nio.file.Path

class ITextServicePdfRenderer : ServicePdfRenderer {

  companion object {
    private val converterProperties: ConverterProperties = ConverterProperties()
  }

  override suspend fun generateServicePdf(servicePdfPath: Path, serviceHtml: InputStream) {
    createWritablePdfDocument(output = servicePdfPath).use { pdf ->
      newDocument(pdf).use {
        HtmlConverter.convertToElements(serviceHtml, converterProperties).forEach { element ->
          when (element) {
            is IBlockElement -> it.add(element)
            is Image -> it.add(element)
            is HtmlPageBreak -> it.add(AreaBreak(AreaBreakType.NEXT_PAGE))
            else -> {
              throw SubjectAccessRequestException("Unsupported element type found ${element.javaClass}")
            }
          }
        }
      }
    }
  }
}
