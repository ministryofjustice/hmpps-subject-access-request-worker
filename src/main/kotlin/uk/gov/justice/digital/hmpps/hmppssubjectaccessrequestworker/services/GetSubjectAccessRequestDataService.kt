package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Font
import com.itextpdf.text.FontFactory
import com.itextpdf.text.pdf.PdfWriter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

@Service
class GetSubjectAccessRequestDataService(@Autowired val genericHmppsApiGateway: GenericHmppsApiGateway) {
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
  fun savePDF(content: Map<String, Any>) {
    val document = Document()
    Files.createDirectories(Path.of("./tmp/pdf"))
    PdfWriter.getInstance(document, FileOutputStream("./tmp/pdf/dummy.pdf"))

    document.open()
    val font: Font = FontFactory.getFont(FontFactory.COURIER, 16f, BaseColor.BLACK)
    content.forEach { entry ->
      document.add(Chunk("${entry.key} : ${entry.value}", font))
    }
    document.close()

//    val document = PDDocument()
//
//    val page = PDPage()
//    document.addPage(page)
//    val contentStream = PDPageContentStream(document, page)
//    contentStream.setFont(PDType1Font.TIMES_ROMAN, 12f)
//    contentStream.beginText()
//    content.forEach { entry ->
//      contentStream.showText("${entry.key} : ${entry.value}")
//    }
//    contentStream.endText()
//    contentStream.close()
//
//    Files.createDirectories(Path.of("./tmp/pdf"))
//
//    document.save("./tmp/pdf/dummy.pdf")
//    document.close()
  }
}
