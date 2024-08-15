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

  fun execute(services: List<DpsService>, nomisId: String? = null, ndeliusId: String? = null, dateFrom: LocalDate? = null, dateTo: LocalDate? = null): List<DpsService> {
    val orderedServices = this.order(services)

    orderedServices.forEach {
      val response: Map<*, *>? = genericHmppsApiGateway.getSarData(it.url!!, nomisId, ndeliusId, dateFrom, dateTo)

      if (response != null && response.containsKey("content")) {
        it.content = response["content"]
      } else {
        it.content = "No Data Held"
      }
    }
    return orderedServices
  }

  fun order(services: List<DpsService>): List<DpsService> {
    val servicesWithNoOrderPosition = mutableListOf<DpsService>()
    val servicesWithOrderPosition = mutableListOf<DpsService>()

    services.forEach { service ->
      if (service.orderPosition != null) {
        servicesWithOrderPosition.add(service)
      } else {
        servicesWithNoOrderPosition.add(service)
      }
    }

    val servicesSortedByOrderPosition = servicesWithOrderPosition.sortedBy { it.orderPosition }
    val servicesWithNoOrderPositionSortedByName = servicesWithNoOrderPosition.sortedBy { it.name }

    return servicesSortedByOrderPosition + servicesWithNoOrderPositionSortedByName
  }
}
