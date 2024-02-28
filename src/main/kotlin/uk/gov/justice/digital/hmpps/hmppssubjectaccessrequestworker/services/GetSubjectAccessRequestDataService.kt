package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
import com.itextpdf.text.pdf.PdfWriter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
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
  fun savePDF(content: Map<String, Any>, fileName: String): Int {
    log.info("Saving report..")
    val document = Document()
    Files.createDirectories(Path.of("/tmp/pdf"))
    log.info("Created path")
    PdfWriter.getInstance(document, FileOutputStream("/tmp/pdf/$fileName"))
    document.open()
    log.info("Started writing to PDF")
    val font: Font = FontFactory.getFont(FontFactory.COURIER, 16f, BaseColor.BLACK)
    log.info("Set font")
    content.forEach { entry ->
      log.info(entry.key + entry.value)
      document.add(Chunk("${entry.key} : ${entry.value}", font))
    }
    log.info("Finished writing report")
    document.close()
    log.info("PDF complete")
    return 0
  }
}
