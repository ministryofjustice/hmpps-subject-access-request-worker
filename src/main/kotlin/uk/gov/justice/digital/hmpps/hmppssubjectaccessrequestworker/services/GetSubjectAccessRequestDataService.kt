package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
import java.time.LocalDate

@Service
class GetSubjectAccessRequestDataService(@Autowired val genericHmppsApiGateway: GenericHmppsApiGateway) {
  fun execute(services: String, nomisId: String? = null, ndeliusId: String? = null, dateFrom: LocalDate? = null, dateTo: LocalDate? = null): Map<String, Any> {
    val responseObject = mutableMapOf<String, Any>()
    val serviceMap = mutableMapOf<String, String>()

    val serviceNames =
      services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 == 0 }
    val serviceUrls =
      services.split(',').map { splitService -> splitService.trim() }.filterIndexed { index, _ -> index % 2 != 0 }
    for (serviceName in serviceNames) {
      serviceMap.put(serviceName, serviceUrls[serviceNames.indexOf(serviceName)])
    }

    serviceMap.forEach { (service, serviceUrl) ->
      val response: Map<*, *>? = genericHmppsApiGateway.getSarData(serviceUrl, nomisId, ndeliusId, dateFrom, dateTo)
      response?.get("content")?.let { responseObject[service] = it }
    }
    return responseObject
  }

  fun savePDF(filePath: String, content: String): String {
    val document = PDDocument()

    val page = PDPage()
    document.addPage(page)
    // File(filePath).writeText(content)
    val contentStream = PDPageContentStream(document, page)
    contentStream.setFont(PDType1Font.TIMES_ROMAN, 12f)
    contentStream.beginText()
    contentStream.showText(content)
    contentStream.endText()
    contentStream.close()

    document.save(filePath)
    document.close()
    // File(filePath).writeText(
    return filePath
  }
}
