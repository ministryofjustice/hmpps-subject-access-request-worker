package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
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
      response?.get("content")?.let { responseObject[service] = it }
    }
    return responseObject
  }
  fun generatePDF(
    content: Map<String, Any>,
    document: Document = Document(),
    pdfStream: ByteArrayOutputStream = ByteArrayOutputStream(),
    pdfService: PdfService = PdfService(),
  ): ByteArrayOutputStream {
    log.info("Saving report..")
    pdfService.getPdfWriter(document, pdfStream)
    document.open()
    log.info("Started writing to PDF")
    val font: Font = FontFactory.getFont(FontFactory.COURIER, 16f, BaseColor.BLACK)
    log.info("Set font")
    if (content == emptyMap<Any, Any>()) {
      document.add(Chunk("NO DATA FOUND", font))
    }
    content.forEach { entry ->
      log.info(entry.key + entry.value)
      document.add(Chunk("${entry.key} : ${entry.value}", font))
    }
    log.info("Finished writing report")
    document.close()
    log.info("PDF complete")
    return pdfStream
  }
}
