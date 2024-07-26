package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.IBlockElement
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
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils.DateConversionHelper
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils.HeadingHelper
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils.ProcessDataHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

const val DATA_HEADER_FONT_SIZE = 16f
const val DATA_FONT_SIZE = 12f
const val DATA_LINE_SPACING = 16f

@Service
class GeneratePdfService {
  companion object {
    var dateConversionHelper = DateConversionHelper()
    var templateRenderService = TemplateRenderService()
  }

  fun execute(
    content: LinkedHashMap<String, Any>,
    nomisId: String?,
    ndeliusCaseReferenceId: String?,
    sarCaseReferenceNumber: String,
    subjectName: String,
    dateFrom: LocalDate? = null,
    dateTo: LocalDate? = null,
    pdfStream: ByteArrayOutputStream = createPdfStream(),
  ): ByteArrayOutputStream {
    log.info("Saving report..")
    val fullDocumentWriter = getPdfWriter(pdfStream)
    val mainPdfStream = createPdfStream()
    val pdfDocument = PdfDocument(PdfWriter(mainPdfStream))
    val document = Document(pdfDocument)
    log.info("Started writing to PDF")
    addInternalContentsPage(pdfDocument, document, content)
    addExternalCoverPage(
      pdfDocument,
      document,
      nomisId,
      ndeliusCaseReferenceId,
      sarCaseReferenceNumber,
      dateFrom,
      dateTo,
    )
    pdfDocument.addEventHandler(
      PdfDocumentEvent.END_PAGE,
      CustomHeaderEventHandler(
        pdfDocument,
        document,
        getSubjectIdLine(nomisId, ndeliusCaseReferenceId),
        sarCaseReferenceNumber,
      ),
    )
    document.setMargins(50F, 50F, 100F, 50F)
    addData(pdfDocument, document, content)
    val numPages = pdfDocument.numberOfPages
    addRearPage(pdfDocument, document, numPages)

    log.info("Finished writing report")
    document.close()

    val coverPdfStream = createPdfStream()
    val coverPage = PdfDocument(PdfWriter(coverPdfStream))
    val coverPageDocument = Document(coverPage)
    addInternalCoverPage(
      coverPageDocument,
      nomisId,
      ndeliusCaseReferenceId,
      sarCaseReferenceNumber,
      dateFrom,
      dateTo,
      content,
      numPages,
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
    fullDocument.close()
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
    endPageText.add(Text("Total pages: ${numPages + 2}\n\n"))
    endPageText.add(Text("INTERNAL ONLY"))
    document.add(endPageText)
  }

  fun addData(pdfDocument: PdfDocument, document: Document, content: LinkedHashMap<String, Any>) {
    content.forEach { entry ->
      log.info("Compiling data from " + entry.key)

      document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
      val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
      val headerParagraph = Paragraph().setFixedLeading(DATA_LINE_SPACING)
      val boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
      log.info("Compiling data from " + entry.key)
      headerParagraph.add(
        Text("${HeadingHelper.format(entry.key)}\n")
          .setFont(boldFont)
          .setFontSize(DATA_HEADER_FONT_SIZE),
      )
      document.add(headerParagraph)

      val processedData = preProcessData(entry.value)

      val renderedTemplate = templateRenderService.renderTemplate(serviceName = entry.key, serviceData = processedData)
      if (renderedTemplate !== null && renderedTemplate !== "") {
        // Template found - render using the data
        val htmlElement = HtmlConverter.convertToElements(renderedTemplate)
        for (element in htmlElement) {
          document.add(element as IBlockElement)
        }
      } else {
        // No template rendered, fallback to old YAML layout
        val fallbackRender = renderAsBasicYaml(serviceData = processedData)
        val contentParagraph = Paragraph().setFixedLeading(DATA_LINE_SPACING)
        contentParagraph.add(fallbackRender).setFont(font).setFontSize(DATA_FONT_SIZE)
        document.add(contentParagraph)
      }
    }
    log.info("Added data to PDF")
  }

  fun renderAsBasicYaml(serviceData: Any?): Text {
    val loaderOptions = LoaderOptions()
    loaderOptions.codePointLimit = 1024 * 1024 * 1024 // Max YAML size 1 GB - can be increased
    val yamlFactory = YAMLFactory.builder()
      .loaderOptions(loaderOptions)
      .build()
    val contentText =
      YAMLMapper(yamlFactory.enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)).writeValueAsString(
        serviceData,
      )
    val text = Text(contentText)
    text.setNextRenderer(CodeRenderer(text))
    return text
  }

  fun addInternalCoverPage(
    document: Document,
    nomisId: String?,
    ndeliusCaseReferenceId: String?,
    sarCaseReferenceNumber: String,
    dateFrom: LocalDate?,
    dateTo: LocalDate?,
    dataFromServices: LinkedHashMap<String, Any>,
    numPages: Int,
  ) {
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val coverpageText = Paragraph().setFont(font).setFontSize(16f).setTextAlignment(TextAlignment.CENTER)
    coverpageText.add(Text("\u00a0\n").setFontSize(180f))
    coverpageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
    document.add(coverpageText)
    document.add(Paragraph(getSubjectIdLine(nomisId, ndeliusCaseReferenceId)).setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph("SAR Case Reference Number: $sarCaseReferenceNumber").setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph(getReportDateRangeLine(dateFrom, dateTo)).setTextAlignment(TextAlignment.CENTER))
    document.add(
      Paragraph(
        "Report generation date: ${
          LocalDate.now().format(
            DateTimeFormatter.ofLocalizedDate(
              FormatStyle.LONG,
            ),
          )
        }",
      ).setTextAlignment(TextAlignment.CENTER),
    )
    document.add(Paragraph("${getServiceListLine(dataFromServices)}\n").setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph("\nTotal Pages: ${numPages + 2}").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
    document.add(Paragraph("\nINTERNAL ONLY").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
    document.add(Paragraph("\nOFFICIAL-SENSITIVE").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
  }

  fun addExternalCoverPage(
    pdfDocument: PdfDocument,
    document: Document,
    nomisId: String?,
    ndeliusCaseReferenceId: String?,
    sarCaseReferenceNumber: String,
    dateFrom: LocalDate?,
    dateTo: LocalDate?,
  ) {
    document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val coverpageText = Paragraph().setFont(font).setFontSize(16f).setTextAlignment(TextAlignment.CENTER)
    coverpageText.add(Text("\u00a0\n").setFontSize(180f))
    coverpageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
    document.add(coverpageText)
    document.add(Paragraph(getSubjectIdLine(nomisId, ndeliusCaseReferenceId)).setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph("SAR Case Reference Number: $sarCaseReferenceNumber").setTextAlignment(TextAlignment.CENTER))
  }

  fun addInternalContentsPage(
    pdfDocument: PdfDocument,
    document: Document,
    dataFromServices: LinkedHashMap<String, Any>,
  ) {
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val contentsPageText = Paragraph().setFont(font).setFontSize(16f).setTextAlignment(TextAlignment.CENTER)
    contentsPageText.add(Text("\n\n\n"))
    contentsPageText.add(Text("CONTENTS\n"))
    document.add(contentsPageText)

    val serviceList = Paragraph()
    dataFromServices.keys.toList().forEach {
      serviceList.add("\u2022 $it\n").setTextAlignment(TextAlignment.CENTER).setFontSize(14f)
    }
    document.add(serviceList)

    document.add(Paragraph("\n\nINTERNAL ONLY").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
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

  fun getServiceListLine(dataFromServices: LinkedHashMap<String, Any>): String {
    val serviceNamesList = dataFromServices.keys.toList().joinToString(", ")
    return "Services: $serviceNamesList"
  }

  fun preProcessData(input: Any?): Any? {
    if (input is Map<*, *>) {
      // If it's a map, process the key
      val returnMap = mutableMapOf<String, Any?>()
      val inputKeys = input.keys
      inputKeys.forEach { key ->
        returnMap[processKey(key.toString())] = preProcessData(input[key])
      }
      return returnMap
    }

    if (input is ArrayList<*> && input.isNotEmpty()) {
      val returnArray = arrayListOf<Any?>()
      input.forEach { value -> returnArray.add(preProcessData(value)) }
      return returnArray
    }

    return processValue(input)
  }

  fun processKey(key: String): String {
    return ProcessDataHelper.camelToSentence(key)
  }

  fun processValue(input: Any?): Any? {
    // Handle null values
    if (input is ArrayList<*> && input.isEmpty() || input == null || input == "null") {
      return "No data held"
    }
    // Handle dates/times
    if (input is String) {
      var processedValue = input
      processedValue = dateConversionHelper.convertDates(processedValue)
      return processedValue
    }

    return input
  }
}

class CodeRenderer(textElement: Text?) : TextRenderer(textElement) {
  override fun getNextRenderer(): IRenderer {
    return CodeRenderer(getModelElement() as Text)
  }

  override fun trimFirst() {}
}
