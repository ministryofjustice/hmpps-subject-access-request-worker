package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
import java.time.LocalDate

@Service
class GetSubjectAccessRequestDataService(@Autowired val genericHmppsApiGateway: GenericHmppsApiGateway) {
  private val log = LoggerFactory.getLogger(this::class.java)
  fun execute(services: MutableMap<String, String>, nomisId: String? = null, ndeliusId: String? = null, dateFrom: LocalDate? = null, dateTo: LocalDate? = null): Map<String, Any> {
    val responseObject = mutableMapOf<String, Any>()

    services.forEach { (service, serviceUrl) ->
      val response: Map<*, *>? = genericHmppsApiGateway.getSarData(serviceUrl, nomisId, ndeliusId, dateFrom, dateTo)
      if (response != null && response.containsKey("content")) {
        responseObject[service] = response["content"] as Any
      } else {
        responseObject[service] = "No Content"
      }
    }
    return responseObject
  }
}
