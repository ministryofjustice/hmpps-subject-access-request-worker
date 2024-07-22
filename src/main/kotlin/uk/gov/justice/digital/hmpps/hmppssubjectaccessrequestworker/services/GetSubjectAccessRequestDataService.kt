package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsServices
import java.time.LocalDate

@Service
class GetSubjectAccessRequestDataService(@Autowired val genericHmppsApiGateway: GenericHmppsApiGateway) {
  private val log = LoggerFactory.getLogger(this::class.java)
  fun execute(services: DpsServices, nomisId: String? = null, ndeliusId: String? = null, dateFrom: LocalDate? = null, dateTo: LocalDate? = null): Map<String, Any> {
    val responseObject = mutableMapOf<String, Any>()

    services.dpsServices.forEach {
      val response: Map<*, *>? = genericHmppsApiGateway.getSarData(it.url!!, nomisId, ndeliusId, dateFrom, dateTo)
      val serviceName = if (it.businessName != null) it.businessName!! else it.name!!

      if (response != null && response.containsKey("content")) {
          responseObject[serviceName] = response["content"] as Any
      } else {
        responseObject[serviceName] = "No Content"
      }
    }
    return responseObject
  }
}
