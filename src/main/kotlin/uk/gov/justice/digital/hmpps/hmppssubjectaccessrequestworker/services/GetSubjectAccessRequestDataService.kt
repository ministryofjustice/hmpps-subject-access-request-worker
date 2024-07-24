package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import java.time.LocalDate

@Service
class GetSubjectAccessRequestDataService(@Autowired val genericHmppsApiGateway: GenericHmppsApiGateway) {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun execute(services: List<DpsService>, nomisId: String? = null, ndeliusId: String? = null, dateFrom: LocalDate? = null, dateTo: LocalDate? = null): LinkedHashMap<String, Any> {
    val responseObject = linkedMapOf<String, Any>()

    val orderedServices = this.order(services)

    orderedServices.forEach {
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

  fun order(services: List<DpsService>): List<DpsService> {
    return services.sortedBy { it.orderPosition }
  }
}
