package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.events.PdfDocumentEvent.END_PAGE
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
import com.microsoft.applicationinsights.TelemetryClient
import org.apache.commons.lang3.time.StopWatch
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.LoaderOptions
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.CustomHeaderEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ReportParameters
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.HeadingHelper
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.ProcessDataHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class PdfService {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private var dateConversionHelper = DateConversionHelper()
    private var templateRenderService = TemplateRenderService()
    private var telemetryClient = TelemetryClient()
    private val helveticaFont = PdfFontFactory.createFont(StandardFonts.HELVETICA)

    private const val BULLET_POINT = "\u2022"
    private const val NO_BREAK_SPACE = "\u00a0"
    private const val TOP_MARGIN = 50F
    private const val RIGHT_MARGIN = 35F
    private const val BOTTOM_MARGIN = 70F
    private const val LEFT_MARGIN = 35F
  }

  /**
   * Generate a Subject Access Request report PDF document from the parameters provided
   */
  fun generateSubjectAccessRequestPDF(reportParams: ReportParameters): ByteArrayOutputStream {
    val body = generateReportBodyPdf(reportParams)
    val cover = generateReportCoverPdf(reportParams, body.numberOfPages)
    return mergeBodyAndCoverDocuments(body, cover)
  }

  private fun mergeBodyAndCoverDocuments(
    body: PdfOutputStreamWrapper,
    cover: PdfOutputStreamWrapper,
  ): ByteArrayOutputStream {
    try {
      val fullDocumentBaos = ByteArrayOutputStream()

      createPdfDocument(fullDocumentBaos).use { fullPdfDocument ->
        val merger = PdfMerger(fullPdfDocument)

        PdfReader(cover.toInputStream()).use { coverReader ->
          PdfDocument(coverReader).use { coverDoc ->
            merger.merge(coverDoc, 1, 1)
          }
        }

        PdfReader(body.toInputStream()).use { bodyReader ->
          PdfDocument(bodyReader).use { bodyDoc ->
            merger.merge(bodyDoc, 1, (bodyDoc.numberOfPages))
          }
        }
      }
      return fullDocumentBaos
    } finally {
      body.baos.close()
      cover.baos.close()
    }
  }

  /**
   * Generates a Subject Access Request PDF report document minus the front page.
   */
  private fun generateReportBodyPdf(params: ReportParameters): PdfOutputStreamWrapper {
    val baos = ByteArrayOutputStream()

    return createPdfDocument(baos).use { pdfDocument ->
      val numberOfPages: Int

      Document(pdfDocument).use { document ->
        document.addInternalContentsPage(params.services)
        document.addExternalCoverPage(
          params.subjectName,
          params.nomisId,
          params.ndeliusCaseReferenceId,
          params.sarCaseReferenceNumber,
        )
        document.addCustomHeaderHandler(
          getSubjectIdLine(params.nomisId, params.ndeliusCaseReferenceId),
          params.subjectName,
        )
        document.setMargins(TOP_MARGIN, RIGHT_MARGIN, BOTTOM_MARGIN, LEFT_MARGIN)
        document.addSubjectAccessRequestServiceData(params.services)

        numberOfPages = document.pdfDocument.numberOfPages
        val numberOfPageIncludingCovers = numberOfPages + 2
        document.addRearPage(numberOfPageIncludingCovers)
      }

      PdfOutputStreamWrapper(baos, numberOfPages)
    }
  }

  /**
   * Generate the front cover of the Subject Access Request report.
   */
  private fun generateReportCoverPdf(params: ReportParameters, numberOfPages: Int): PdfOutputStreamWrapper {
    val baos = ByteArrayOutputStream()
    return createPdfDocument(baos).use { pdfDocument ->
      Document(pdfDocument).use { document ->
        document.addInternalCoverPage(
          params.subjectName,
          params.nomisId,
          params.ndeliusCaseReferenceId,
          params.sarCaseReferenceNumber,
          params.dateFrom,
          params.dateTo,
          params.services,
          numberOfPages,
        )
      }
      PdfOutputStreamWrapper(baos, numberOfPages)
    }
  }

  fun createPdfDocument(outputStream: OutputStream): PdfDocument = PdfDocument(PdfWriter(outputStream))

  private fun Document.addExternalCoverPage(
    subjectName: String,
    nomisId: String?,
    ndeliusCaseReferenceId: String?,
    sarCaseReferenceNumber: String,
  ) {
    this.add(AreaBreak(AreaBreakType.NEXT_PAGE))
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)

    this.add(
      Paragraph()
        .setFont(font)
        .setFontSize(16f)
        .setTextAlignment(TextAlignment.CENTER)
        .add(Text("$NO_BREAK_SPACE\n").setFontSize(180f))
        .add(Text("SUBJECT ACCESS REQUEST REPORT\n\n")),
    )

    this.add(
      Paragraph("Name: $subjectName")
        .setTextAlignment(TextAlignment.CENTER),
    )

    this.add(
      Paragraph(getSubjectIdLine(nomisId, ndeliusCaseReferenceId))
        .setTextAlignment(TextAlignment.CENTER),
    )

    this.add(
      Paragraph("SAR Case Reference Number: $sarCaseReferenceNumber")
        .setTextAlignment(TextAlignment.CENTER),
    )
  }


  private fun Document.addInternalContentsPage(services: List<DpsService>) {
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
    val contentsPageText = Paragraph().setFont(font).setFontSize(16f).setTextAlignment(TextAlignment.CENTER)
    contentsPageText.add(Text("\n\n\n"))
    contentsPageText.add(Text("CONTENTS\n"))
    this.add(contentsPageText)

    val serviceList = Paragraph()
    services.forEach {
      serviceList
        .add("$BULLET_POINT ${it.businessName ?: it.name}\n")
        .setTextAlignment(TextAlignment.CENTER)
        .setFontSize(14f)
    }
    this.add(serviceList)

    this.add(
      Paragraph("\n\nINTERNAL ONLY")
        .setTextAlignment(TextAlignment.CENTER)
        .setFontSize(16f),
    )
  }

  private fun getSubjectIdLine(nomisId: String?, ndeliusCaseReferenceId: String?): String {
    return if (nomisId != null) {
      "NOMIS ID: $nomisId"
    } else {
      "nDelius ID: $ndeliusCaseReferenceId"
    }
  }

  private fun Document.addCustomHeaderHandler(subjectLineId: String, subjectName: String) {
    this.pdfDocument.addEventHandler(
      END_PAGE, CustomHeaderEventHandler(this.pdfDocument, this, subjectLineId, subjectName),
    )
  }

  private fun Document.addSubjectAccessRequestServiceData(services: List<DpsService>) {
    services.forEach { service ->
      this.add(AreaBreak(AreaBreakType.NEXT_PAGE))
      val stopWatch = StopWatch.createStarted()

      if (service.content != "No Data Held") {
        val renderedTemplate = templateRenderService.renderTemplate(
          serviceName = service.name!!,
          serviceData = service.content,
        )

        if (renderedTemplate !== null && renderedTemplate !== "") {
          // Template found - render using the data
          val htmlElement = HtmlConverter.convertToElements(renderedTemplate)

          for (element in htmlElement) {
            this.add(element as IBlockElement)
          }
          log.info("Template rendered - copying complete")
        } else {
          this.addRawServiceDataWithYamlLayout(service)
        }
      } else {
        // No template rendered, fallback to old YAML layout
        this.addRawServiceDataWithYamlLayout(service)
      }

      stopWatch.stop()
    }
    log.info("Added data to PDF")
  }

  fun Document.addRearPage(numberOfPages: Int) {
    this.add(AreaBreak(AreaBreakType.NEXT_PAGE))
    val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)

    val endPageText = Paragraph()
      .setFont(font)
      .setFontSize(16f)
      .setTextAlignment(TextAlignment.CENTER)

    this.add(Paragraph(NO_BREAK_SPACE).setFontSize(300f))

    endPageText.add(Text("End of Subject Access Request Report\n\n"))
    endPageText.add(Text("Total pages: $numberOfPages\n\n"))
    endPageText.add(Text("INTERNAL ONLY"))
    this.add(endPageText)
  }

  fun Document.addRawServiceDataWithYamlLayout(service: DpsService) {
    this.add(
      Paragraph()
        .setFixedLeading(DATA_LINE_SPACING)
        .add(Text("${service.businessName ?: HeadingHelper.format(service.name!!)}\n"))
        .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
        .setFontSize(DATA_HEADER_FONT_SIZE),
    )
    val processedData = preProcessData(service.content)
    this.add(
      Paragraph()
        .setFixedLeading(DATA_LINE_SPACING)
        .add(renderAsBasicYaml(serviceData = processedData))
        .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
        .setFontSize(DATA_FONT_SIZE),
    )
  }

  fun Document.addInternalCoverPage(
    subjectName: String,
    nomisId: String?,
    ndeliusCaseReferenceId: String?,
    sarCaseReferenceNumber: String,
    dateFrom: LocalDate?,
    dateTo: LocalDate?,
    services: List<DpsService>,
    numberOfPages: Int,
  ) {
    val coverPageText = Paragraph()
      .setFont(helveticaFont)
      .setFontSize(16f)
      .setTextAlignment(TextAlignment.CENTER)

    coverPageText.add(
      Text("$NO_BREAK_SPACE\n")
        .setFontSize(180f),
    )
    coverPageText.add(Text("SUBJECT ACCESS REQUEST REPORT\n\n"))

    this.add(coverPageText)
      .setTextAlignment(TextAlignment.CENTER)

    this.add(
      Paragraph("Name: $subjectName")
        .setTextAlignment(TextAlignment.CENTER),
    )
    this.add(
      Paragraph(getSubjectIdLine(nomisId, ndeliusCaseReferenceId))
        .setTextAlignment(TextAlignment.CENTER),
    )
    this.add(
      Paragraph("SAR Case Reference Number: $sarCaseReferenceNumber")
        .setTextAlignment(TextAlignment.CENTER),
    )
    this.add(
      Paragraph(getReportDateRangeLine(dateFrom, dateTo))
        .setTextAlignment(TextAlignment.CENTER),
    )
    this.add(
      Paragraph("Report generation date: ${reportGenerationDate()}")
        .setTextAlignment(TextAlignment.CENTER),
    )

    val serviceListLine = services.joinToString(",") { it.businessName ?: it.name!! }
    this.add(Paragraph("Services: $serviceListLine\n").setTextAlignment(TextAlignment.CENTER))

    this.add(
      Paragraph("\nTotal Pages: ${numberOfPages + 2}")
        .setTextAlignment(TextAlignment.CENTER)
        .setFontSize(16f),
    )

    this.add(
      Paragraph("\nINTERNAL ONLY")
        .setTextAlignment(TextAlignment.CENTER)
        .setFontSize(16f),
    )

    this.add(
      Paragraph("\nOFFICIAL-SENSITIVE")
        .setTextAlignment(TextAlignment.CENTER)
        .setFontSize(16f),
    )
  }

  fun reportGenerationDate() = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))

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
      processedValue = GeneratePdfService.dateConversionHelper.convertDates(processedValue)
      return processedValue
    }

    return input
  }

  private data class PdfOutputStreamWrapper(val baos: ByteArrayOutputStream, val numberOfPages: Int) {
    fun toInputStream(): ByteArrayInputStream {
      return ByteArrayInputStream(baos.toByteArray())
    }
  }
}