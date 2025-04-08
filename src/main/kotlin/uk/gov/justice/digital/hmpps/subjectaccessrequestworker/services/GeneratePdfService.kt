package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
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
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.yaml.snakeyaml.LoaderOptions
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config.trackSarEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.CustomHeaderEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.HeadingHelper
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.ProcessDataHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val DATA_HEADER_FONT_SIZE = 16f
const val DATA_FONT_SIZE = 12f
const val DATA_LINE_SPACING = 16f
const val NO_DATA_HELD = "No Data Held"

/**
 * This is the legacy implementation, see [PdfService] for new.
 */
@Service
class GeneratePdfService(
  val templateRenderService: TemplateRenderService,
  val telemetryClient: TelemetryClient,
  val dateService: DateService,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val dateConverter: DateConversionHelper = DateConversionHelper()
    val reportDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  }

  fun execute(
    services: List<DpsService>,
    subjectName: String,
    sar: SubjectAccessRequest,
    pdfStream: ByteArrayOutputStream = createPdfStream(),
  ): ByteArrayOutputStream {
    log.info("Saving report..")
    val fullDocumentWriter = getPdfWriter(pdfStream)
    val mainPdfStream = createPdfStream()
    val pdfDocument = PdfDocument(PdfWriter(mainPdfStream))
    val document = Document(pdfDocument)

    log.info("Started writing to PDF")
    telemetryClient.trackSarEvent("PDFContentGenerationStarted", sar, "numServices" to services.size.toString())
    addInternalContentsPage(pdfDocument, document, services)
    addExternalCoverPage(
      pdfDocument,
      document,
      subjectName,
      sar.nomisId,
      sar.ndeliusCaseReferenceId,
      sar.sarCaseReferenceNumber,
      sar.dateFrom,
      sar.dateTo,
    )
    pdfDocument.addEventHandler(
      PdfDocumentEvent.END_PAGE,
      CustomHeaderEventHandler(
        pdfDocument,
        document,
        getSubjectIdLine(sar.nomisId, sar.ndeliusCaseReferenceId),
        subjectName,
      ),
    )
    document.setMargins(50F, 35F, 70F, 35F)
    addData(pdfDocument, document, services, sar)
    val numPages = pdfDocument.numberOfPages
    addRearPage(pdfDocument, document, numPages)

    telemetryClient.trackSarEvent("PDFContentGenerationComplete", sar, "numPages" to numPages.toString())

    log.info("Finished writing report")
    document.close()

    val coverPdfStream = createPdfStream()
    val coverPage = PdfDocument(PdfWriter(coverPdfStream))
    val coverPageDocument = Document(coverPage)
    addInternalCoverPage(
      coverPageDocument,
      subjectName,
      sar.nomisId,
      sar.ndeliusCaseReferenceId,
      sar.sarCaseReferenceNumber,
      sar.dateFrom,
      sar.dateTo,
      numPages,
    )
    coverPageDocument.close()

    val fullDocument = PdfDocument(fullDocumentWriter)
    val merger = PdfMerger(fullDocument)
    val cover = PdfDocument(PdfReader(ByteArrayInputStream(coverPdfStream.toByteArray())))
    val mainContent = PdfDocument(PdfReader(ByteArrayInputStream(mainPdfStream.toByteArray())))

    telemetryClient.trackSarEvent("PDFMergingStarted", sar)
    merger.merge(cover, 1, 1)
    merger.merge(mainContent, 1, mainContent.numberOfPages)
    telemetryClient.trackSarEvent("PDFMergingComplete", sar, "numPages" to fullDocument.numberOfPages.toString())

    cover.close()
    mainContent.close()
    fullDocument.close()
    log.info("PDF complete")

    return pdfStream
  }

  fun getPdfWriter(stream: ByteArrayOutputStream): PdfWriter = PdfWriter(stream)

  fun createPdfStream(): ByteArrayOutputStream = ByteArrayOutputStream()

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

  fun addData(
    pdfDocument: PdfDocument,
    document: Document,
    services: List<DpsService>,
    subjectAccessRequest: SubjectAccessRequest? = null,
  ) {
    services.forEach { service ->
      document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
      log.info("Compiling data from ${service.businessName ?: service.name}")

      val stopWatch = StopWatch.createStarted()
      val templatingStopWatch = StopWatch.create()
      val convertingStopWatch = StopWatch.create()
      val appendingStopWatch = StopWatch.create()

      telemetryClient.trackSarEvent(
        "PDFServiceContentGenerationStarted",
        subjectAccessRequest,
        "service" to (service.name ?: "unknown"),
      )

      if (service.content != NO_DATA_HELD) {
        templatingStopWatch.start()
        val renderedTemplate = templateRenderService.renderTemplate(
          subjectAccessRequest = subjectAccessRequest,
          serviceName = service.name!!,
          serviceData = service.content,
        )
        templatingStopWatch.stop()

        telemetryClient.trackSarEvent(
          "HTMLServiceContentGenerated",
          subjectAccessRequest,
          "eventTime" to templatingStopWatch.formatTime(),
          "service" to (service.name ?: "unknown"),
          "htmlStringSize" to renderedTemplate?.length.toString(),
        )
        if (StringUtils.isNotEmpty(renderedTemplate)) {
          convertingStopWatch.start()
          val htmlElement = HtmlConverter.convertToElements(renderedTemplate)
          convertingStopWatch.stop()

          telemetryClient.trackSarEvent(
            "HTMLServiceContentConvertedToITextElements",
            subjectAccessRequest,
            "eventTime" to convertingStopWatch.formatTime(),
            "service" to (service.name ?: "unknown"),
            "htmlStringSize" to renderedTemplate!!.length.toString(),
            "elements" to htmlElement.size.toString(),
          )

          appendingStopWatch.start()
          for (element in htmlElement) {
            document.add(element as IBlockElement)
          }
          appendingStopWatch.stop()

          log.info("Template rendered - copying complete")
          telemetryClient.trackSarEvent(
            "ServiceContentITextElementsAppendedToPDF",
            subjectAccessRequest,
            "eventTime" to appendingStopWatch.formatTime(),
            "service" to (service.name ?: "unknown"),
            "htmlStringSize" to renderedTemplate.length.toString(),
            "elements" to htmlElement.size.toString(),
          )
        } else {
          addYamlLayout(document, service)
        }
      } else {
        // No template rendered, fallback to old YAML layout
        addNoDataHeldPage(document, service)
      }

      stopWatch.stop()
      telemetryClient.trackSarEvent(
        "PDFServiceContentGenerationComplete",
        subjectAccessRequest,
        "service" to (service.name ?: "unknown"),
        "numPages" to pdfDocument.numberOfPages.toString(),
        "totalTime" to stopWatch.formatTime(),
        "templatingTime" to templatingStopWatch.formatTime(),
        "convertingTime" to convertingStopWatch.formatTime(),
        "appendingTime" to appendingStopWatch.formatTime(),
      )
    }
    log.info("Added data to PDF")
  }

  fun addNoDataHeldPage(document: Document, service: DpsService) {
    document.add(
      Paragraph()
        .setFixedLeading(DATA_LINE_SPACING)
        .add(Text("${service.businessName ?: HeadingHelper.format(service.name!!)}\n"))
        .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
        .setFontSize(DATA_HEADER_FONT_SIZE),
    )
    document.add(
      Paragraph()
        .setFixedLeading(DATA_LINE_SPACING)
        .add(Text(NO_DATA_HELD))
        .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
        .setFontSize(DATA_FONT_SIZE),
    )
  }

  fun addYamlLayout(document: Document, service: DpsService) {
    document.add(
      Paragraph()
        .setFixedLeading(DATA_LINE_SPACING)
        .add(Text("${service.businessName ?: HeadingHelper.format(service.name!!)}\n"))
        .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
        .setFontSize(DATA_HEADER_FONT_SIZE),
    )
    val processedData = preProcessData(service.content)
    document.add(
      Paragraph()
        .setFixedLeading(DATA_LINE_SPACING)
        .add(renderAsBasicYaml(serviceData = processedData))
        .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
        .setFontSize(DATA_FONT_SIZE),
    )
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
    subjectName: String,
    nomisId: String?,
    ndeliusCaseReferenceId: String?,
    sarCaseReferenceNumber: String,
    dateFrom: LocalDate?,
    dateTo: LocalDate?,
    numPages: Int,
  ) {
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val coverpageText = Paragraph().setFont(font).setFontSize(16f).setTextAlignment(TextAlignment.CENTER)
    coverpageText.add(Text("\u00a0\n").setFontSize(180f))
    coverpageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))
    document.add(coverpageText).setTextAlignment(TextAlignment.CENTER)
    document.add(Paragraph("Name: $subjectName")).setTextAlignment(TextAlignment.CENTER)
    document.add(Paragraph(getSubjectIdLine(nomisId, ndeliusCaseReferenceId)).setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph("SAR Case Reference Number: $sarCaseReferenceNumber").setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph(getReportDateRangeLine(dateFrom, dateTo)).setTextAlignment(TextAlignment.CENTER))
    document.add(
      Paragraph("Report generation date: ${dateService.reportGenerationDate()}").setTextAlignment(
        TextAlignment.CENTER,
      ),
    )
    document.add(Paragraph("\nTotal Pages: ${numPages + 2}").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
    document.add(Paragraph("\nINTERNAL ONLY").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
    document.add(Paragraph("\nOFFICIAL-SENSITIVE").setTextAlignment(TextAlignment.CENTER).setFontSize(16f))
  }

  fun addExternalCoverPage(
    pdfDocument: PdfDocument,
    document: Document,
    subjectName: String,
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
    document.add(Paragraph("Name: $subjectName").setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph(getSubjectIdLine(nomisId, ndeliusCaseReferenceId)).setTextAlignment(TextAlignment.CENTER))
    document.add(Paragraph("SAR Case Reference Number: $sarCaseReferenceNumber").setTextAlignment(TextAlignment.CENTER))
  }

  fun addInternalContentsPage(
    pdfDocument: PdfDocument,
    document: Document,
    services: List<DpsService>,
  ) {
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val contentsPageText = Paragraph().setFont(font).setFontSize(16f).setTextAlignment(TextAlignment.CENTER)
    contentsPageText.add(Text("\n\n\n"))
    contentsPageText.add(Text("CONTENTS\n"))
    document.add(contentsPageText)

    val serviceList = Paragraph()
    services.forEach {
      serviceList.add("\u2022 ${it.businessName ?: it.name}\n").setTextAlignment(TextAlignment.CENTER).setFontSize(14f)
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
    val formattedDateTo = dateTo!!.format(reportDateFormat)
    val formattedDateFrom: String
    if (dateFrom != null) {
      formattedDateFrom = dateFrom.format(reportDateFormat)
    } else {
      formattedDateFrom = "Start of record"
    }
    return "Report date range: $formattedDateFrom - $formattedDateTo"
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

  fun processKey(key: String): String = ProcessDataHelper.camelToSentence(key)

  fun processValue(input: Any?): Any? {
    // Handle null values
    if (input is ArrayList<*> && input.isEmpty() || input == null || input == "null") {
      return "No data held"
    }
    // Handle dates/times
    if (input is String) {
      var processedValue = input
      processedValue = dateConverter.convertDates(processedValue)
      return processedValue
    }

    return input
  }
}

class CodeRenderer(textElement: Text?) : TextRenderer(textElement) {
  override fun getNextRenderer(): IRenderer = CodeRenderer(getModelElement() as Text)

  override fun trimFirst() {}
}
