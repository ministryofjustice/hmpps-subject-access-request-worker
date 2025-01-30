package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository

@Service
class ServiceConfigurationService(private val serviceConfigurationRepository: ServiceConfigurationRepository) {

  fun getSelectedServices(subjectAccessRequest: SubjectAccessRequest): List<DpsService> {
    val selectedServices = subjectAccessRequest.services.split(",").map { serviceName ->
      val service = serviceConfigurationRepository.findByServiceName(serviceName) ?: throw RuntimeException("TODO")
      DpsService(
        name = service.serviceName,
        businessName = service.label,
        url = service.url,
        orderPosition = service.order,
        content = null,
      )
    }

    return selectedServices.sortedBy { it.orderPosition }
  }
}