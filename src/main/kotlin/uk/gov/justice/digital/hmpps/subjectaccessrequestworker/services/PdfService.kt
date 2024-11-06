package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.CustomHeaderEventHandler
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ReportParameters
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.DateConversionHelper
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils.HeadingHelper
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
    private var yamlFormatter = YamlFormatter()
    private var telemetryClient = TelemetryClient()
    private val reportDateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)

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

  /**
   * Generates a Subject Access Request PDF report document minus the front page.
   */
  private fun generateReportBodyPdf(params: ReportParameters): PdfOutputStreamWrapper {
    // Don't auto close here it will be read from when we build the full document.
    val baos = ByteArrayOutputStream()

    return Document(createPdfDocument(baos)).use { document ->
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

      val numberOfPages: Int = document.pdfDocument.numberOfPages
      val numberOfPageIncludingCovers = numberOfPages + 2
      document.addRearPage(numberOfPageIncludingCovers)

      PdfOutputStreamWrapper(baos, numberOfPages)
    }
  }

  /**
   * Generate the front cover of the Subject Access Request report. The cover requires page count but this isn't known
   * until after we've generated the body document hence creating 2 separate documents and then merging them together.
   */
  private fun generateReportCoverPdf(params: ReportParameters, numberOfPages: Int): PdfOutputStreamWrapper {
    // Don't auto close here it will be read from when we build the full document.
    val baos = ByteArrayOutputStream()
    return Document(createPdfDocument(baos)).use { document ->
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
      PdfOutputStreamWrapper(baos, numberOfPages)
    }
  }

  /**
   * Merge the cover and body into a single document.
   */
  private fun mergeBodyAndCoverDocuments(
    body: PdfOutputStreamWrapper,
    cover: PdfOutputStreamWrapper,
  ): ByteArrayOutputStream {
    try {
      val fullDocumentBaos = ByteArrayOutputStream()

      createPdfDocument(fullDocumentBaos).use { fullPdfDocument ->
        val merger = PdfMerger(fullPdfDocument)

        PdfDocument(PdfReader(cover.toInputStream())).use { coverDoc ->
          merger.merge(coverDoc, 1, 1)
        }

        PdfDocument(PdfReader(body.toInputStream())).use { bodyDoc ->
          merger.merge(bodyDoc, 1, (bodyDoc.numberOfPages))
        }
      }
      return fullDocumentBaos
    } finally {
      body.baos.close()
      cover.baos.close()
    }
  }

  private fun createPdfDocument(outputStream: OutputStream): PdfDocument = PdfDocument(PdfWriter(outputStream))

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
    val contentsPageText = Paragraph()
      .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
      .setFontSize(16f)
      .setTextAlignment(TextAlignment.CENTER)

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
      END_PAGE,
      CustomHeaderEventHandler(
        this.pdfDocument,
        this,
        subjectLineId,
        subjectName,
      ),
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
    val serviceDataAsYaml = yamlFormatter.renderAsBasicYaml(service.content)
    this.add(
      Paragraph()
        .setFixedLeading(DATA_LINE_SPACING)
        .add(serviceDataAsYaml)
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
      .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
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

  private fun reportGenerationDate() = LocalDate.now().format(reportDateFormat)

  private fun getReportDateRangeLine(dateFrom: LocalDate?, dateTo: LocalDate?): String {
    val formattedDateTo = dateTo!!.format(reportDateFormat)
    val formattedDateFrom = dateFrom?.format(reportDateFormat) ?: "Start of record"
    return "Report date range: $formattedDateFrom - $formattedDateTo"
  }

  private data class PdfOutputStreamWrapper(val baos: ByteArrayOutputStream, val numberOfPages: Int) {
    fun toInputStream(): ByteArrayInputStream {
      return ByteArrayInputStream(baos.toByteArray())
    }
  }
}
