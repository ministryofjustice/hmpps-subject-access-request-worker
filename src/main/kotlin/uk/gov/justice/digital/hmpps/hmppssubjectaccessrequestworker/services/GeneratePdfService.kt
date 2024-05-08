package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.renderer.IRenderer
import com.itextpdf.layout.renderer.TextRenderer
import org.hibernate.query.sqm.tree.SqmNode.log
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.LoaderOptions
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.CustomHeaderEventHandler
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Service
class GeneratePdfService {
  fun execute(
    content: Map<String, Any>,
    nomisId: String?,
    ndeliusCaseReferenceId: String?,
    sarCaseReferenceNumber: String,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    serviceMap: MutableMap<String, String>,
    pdfStream: ByteArrayOutputStream = createPdfStream(),
  ): ByteArrayOutputStream {
    log.info("Saving report..")
    val writer = getPdfWriter(pdfStream)
    val pdfDocument = PdfDocument(writer)
    val document = Document(pdfDocument)
    log.info("Started writing to PDF")
    addCoverpage(pdfDocument, document, nomisId, ndeliusCaseReferenceId, sarCaseReferenceNumber, dateFrom, dateTo, serviceMap)
    pdfDocument.addEventHandler(PdfDocumentEvent.END_PAGE, CustomHeaderEventHandler(pdfDocument, document, getSubjectIdLine(nomisId, ndeliusCaseReferenceId), sarCaseReferenceNumber))
    document.setMargins(50F, 50F, 100F, 50F)
    addData(pdfDocument, document, content)
    addRearPage(pdfDocument, document, pdfDocument.numberOfPages)
    log.info("Finished writing report")
    document.close()
    log.info("PDF complete")
    return pdfStream
  }

  fun getPdfWriter(stream: ByteArrayOutputStream): PdfWriter {
    return PdfWriter(stream)
  }

  fun createPdfStream(): ByteArrayOutputStream {
    return ByteArrayOutputStream()
  }

  fun addRearPage(pdfDocument: PdfDocument, document: Document, numPages: Int) {
    document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val endPageText = Paragraph().setFont(font).setFontSize(16f).setTextAlignment(TextAlignment.CENTER)
    document.add(Paragraph("\u00a0").setFontSize(300f))
    endPageText.add(Text("End of Subject Access Request Report\n\n"))
    endPageText.add(Text("Total pages: $numPages"))
    document.add(endPageText)
  }

  fun addData(pdfDocument: PdfDocument, document: Document, content: Map<String, Any>) {
    document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val para = Paragraph().setFont(font).setFontSize(12f)
    val boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
    content.forEach { entry ->
      log.info("Compiling data from " + entry.key)
      para.add(Text("${entry.key}\n").setFont(boldFont).setFontSize(18f))
      val loaderOptions = LoaderOptions()
      loaderOptions.codePointLimit = 1024 * 1024 * 1024 // Max YAML size 1 GB - can be increased
      val yamlFactory = YAMLFactory.builder()
        .loaderOptions(loaderOptions)
        .build()
      val contentText = YAMLMapper(yamlFactory).writeValueAsString(entry.value)
      val text = Text(contentText)
      text.setNextRenderer(CodeRenderer(text))
      para.add(text)
      para.add("\n")
      log.info("Compiling data from " + entry.key)
    }
    log.info("Adding data to PDF")
    document.add(para)
    log.info("Added data to PDF")
  }

  fun addCoverpage(pdfDocument: PdfDocument, document: Document, nomisId: String?, ndeliusCaseReferenceId: String?, sarCaseReferenceNumber: String, dateFrom: LocalDate?, dateTo: LocalDate?, serviceMap: MutableMap<String, String>) {
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val coverpageText = Paragraph().setFont(font).setFontSize(16f).setTextAlignment(TextAlignment.CENTER)
    coverpageText.add(Text("\u00a0\n").setFontSize(200f))
    coverpageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
    document.add(coverpageText)
    document.add(Paragraph(getSubjectIdLine(nomisId, ndeliusCaseReferenceId)).setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph("SAR Case Reference Number: $sarCaseReferenceNumber").setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph(getReportDateRangeLine(dateFrom, dateTo)).setTextAlignment(TextAlignment.CENTER))
    document.add(
      Paragraph(
        "Report generation date: ${LocalDate.now().format(
          DateTimeFormatter.ofLocalizedDate(
            FormatStyle.LONG,
          ),
        )}",
      ).setTextAlignment(TextAlignment.CENTER),
    )
    document.add(Paragraph("${getServiceListLine(serviceMap)}\n").setTextAlignment(TextAlignment.CENTER))
  }

  fun getSubjectIdLine(nomisId: String?, ndeliusCaseReferenceId: String?): String {
    var subjectIdLine = ""
    if (nomisId != null) {
      subjectIdLine = "NOMIS ID: $nomisId"
    } else if (ndeliusCaseReferenceId != null) {
      subjectIdLine = "nDelius ID: $ndeliusCaseReferenceId"
    }
    return subjectIdLine
  }

  fun getReportDateRangeLine(dateFrom: LocalDate?, dateTo: LocalDate?): String {
    val formattedDateTo = dateTo!!.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    val formattedDateFrom: String
    if (dateFrom != null) {
      formattedDateFrom = dateFrom.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
    } else {
      formattedDateFrom = "Start of record"
    }
    return "Report date range: $formattedDateFrom - $formattedDateTo"
  }

  fun getServiceListLine(serviceMap: MutableMap<String, String>): String {
    val serviceList = serviceMap.keys.toList().joinToString(", ")
    return "Services: $serviceList"
  }
}

class CodeRenderer(textElement: Text?) : TextRenderer(textElement) {
  override fun getNextRenderer(): IRenderer {
    return CodeRenderer(getModelElement() as Text)
  }

  override fun trimFirst() {}
}
