package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
import com.itextpdf.text.Paragraph
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@Service
class GetSubjectAccessRequestDataService(@Autowired val genericHmppsApiGateway: GenericHmppsApiGateway) {
  private val log = LoggerFactory.getLogger(this::class.java)
  fun execute(services: String, nomisId: String? = null, ndeliusId: String? = null, dateFrom: LocalDate? = null, dateTo: LocalDate? = null): Map<String, Any> {
    val responseObject = mutableMapOf<String, Any>()
    val serviceMap = mutableMapOf<String, String>()

    val serviceNames = services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 == 0 }
    val serviceUrls = services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 != 0 }
    for (serviceName in serviceNames) {
      serviceMap.put(serviceName, serviceUrls[serviceNames.indexOf(serviceName)])
    }

    serviceMap.forEach { (service, serviceUrl) ->
      val response: Map<*, *>? = genericHmppsApiGateway.getSarData(serviceUrl, nomisId, ndeliusId, dateFrom, dateTo)
      if (response != null && response.containsKey("content")) {
        responseObject[service] = response["content"] as Any
      } else {
        responseObject[service] = "No Content"
      }
    }
    return responseObject
  }
  fun generatePDF(
    content: Map<String, Any>,
    nID: String,
    sarID: String,
    document: Document = Document(),
    pdfStream: ByteArrayOutputStream = ByteArrayOutputStream(),
    pdfService: PdfService = PdfService(),
  ): ByteArrayOutputStream {
    log.info("Saving report..")
    val writer = pdfService.getPdfWriter(document, pdfStream)
    val event = pdfService.getCustomHeader(nID, sarID)
    pdfService.setEvent(writer, event)
    document.setMargins(50F, 50F, 100F, 50F)
    document.open()
    log.info("Started writing to PDF")
    this.addData(document, content)
    log.info("Finished writing report")
    document.close()
    log.info("PDF complete")
    return pdfStream
  }

  fun addData(document: Document, content: Map<String, Any>) {
    val para = Paragraph()
    val font = FontFactory.getFont(FontFactory.COURIER, 12f, BaseColor.BLACK)
    val boldFont = Font(Font.FontFamily.COURIER, 14f, Font.BOLD)
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
}
