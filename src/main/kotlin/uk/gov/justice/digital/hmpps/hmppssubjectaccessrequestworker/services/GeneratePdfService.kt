package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfPageEventHelper
import com.itextpdf.text.pdf.PdfWriter
import org.hibernate.query.sqm.tree.SqmNode.log
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.CustomHeader
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
    document: Document = createDocument(),
    pdfStream: ByteArrayOutputStream = createPdfStream(),
  ): ByteArrayOutputStream {
    log.info("Saving report..")
    val writer = getPdfWriter(document, pdfStream)
    val event = getCustomHeader(getSubjectIdLine(nomisId, ndeliusCaseReferenceId), sarCaseReferenceNumber)
    setEvent(writer, event)
    document.setMargins(50F, 50F, 100F, 50F)
    document.open()
    log.info("Started writing to PDF")
    addCoverpage(document, nomisId, ndeliusCaseReferenceId, sarCaseReferenceNumber, dateFrom, dateTo, serviceMap)
    addData(document, content)
    log.info("Finished writing report")
    addRearPage(document, writer.pageNumber)
    document.close()
    log.info("PDF complete")
    return pdfStream
  }

  fun getPdfWriter(document: Document, stream: ByteArrayOutputStream): PdfWriter {
    return PdfWriter.getInstance(document, stream)
  }

  fun createDocument(): Document {
    return Document()
  }

  fun createPdfStream(): ByteArrayOutputStream {
    return ByteArrayOutputStream()
  }

  fun getCustomHeader(nID: String, sarID: String): CustomHeader {
    return CustomHeader(nID, sarID)
  }

  fun setEvent(writer: PdfWriter, event: PdfPageEventHelper): Int {
    writer.pageEvent = event
    return 0
  }

  fun addRearPage(document: Document, numPages: Int) {
    document.newPage()
    val endPageText = Paragraph()
    document.add(Paragraph(300f, "\u00a0"))
    endPageText.alignment = Element.ALIGN_CENTER
    val font: Font = FontFactory.getFont(FontFactory.COURIER, 16f, BaseColor.BLACK)
    endPageText.add(Chunk("End of Subject Access Request Report\n\n", font))
    endPageText.add(Chunk("Total pages: $numPages", font))
    document.add(endPageText)
  }

  fun addData(document: Document, content: Map<String, Any>) {
    document.newPage()
    val para = Paragraph()
    val font = FontFactory.getFont(FontFactory.COURIER, 16f, BaseColor.BLACK)
    val boldFont = Font(Font.FontFamily.COURIER, 18f, Font.BOLD)
    content.forEach { entry ->
      log.info(entry.key + entry.value)
      para.add(
        Chunk(
          "${entry.key}\n" + "\n",
          boldFont,
        ),
      )
      if (entry.value is Map<*, *>) {
        (entry.value as Map<*, *>).forEach { value ->
          para.add(
            Chunk(
              "  ${value.key} : ${value.value}\n\n\n",
              font,
            ),
          )
        }
      } else {
        para.add(
          Chunk(
            "  ${entry.value}\n" + "\n" + "\n",
            font,
          ),
        )
      }
    }
    document.add(para)
  }

  fun addCoverpage(document: Document, nomisId: String?, ndeliusCaseReferenceId: String?, sarCaseReferenceNumber: String, dateFrom: LocalDate?, dateTo: LocalDate?, serviceMap: MutableMap<String, String>) {
    document.newPage()

    val coverpageText = Paragraph()
    document.add(Paragraph(300f, "\u00a0"))
    coverpageText.alignment = Element.ALIGN_CENTER
    val font: Font = FontFactory.getFont(FontFactory.COURIER, 16f, BaseColor.BLACK)

    coverpageText.add(Chunk("SUBJECT ACCESS REQUEST REPORT\n\n", font))
    coverpageText.add(Chunk("${getSubjectIdLine(nomisId, ndeliusCaseReferenceId)}\n", font))
    coverpageText.add(Chunk("SAR Case Reference Number: $sarCaseReferenceNumber\n", font))
    coverpageText.add(Chunk("${getReportDateRangeLine(dateFrom, dateTo)}\n", font))
    coverpageText.add(
      Chunk(
        "Report generation date: ${LocalDate.now().format(
          DateTimeFormatter.ofLocalizedDate(
            FormatStyle.LONG,
          ),
        )}\n",
        font,
      ),
    )
    coverpageText.add(Paragraph(getServiceListLine(serviceMap), font))

    document.add(coverpageText)
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
    } else { formattedDateFrom = "Start of record" }
    return "Report date range: $formattedDateFrom - $formattedDateTo"
  }

  fun getServiceListLine(serviceMap: MutableMap<String, String>): String {
    val serviceList = serviceMap.keys.toList().joinToString(", ")
    return "Services: $serviceList"
  }
}
