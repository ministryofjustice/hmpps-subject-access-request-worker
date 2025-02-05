package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository

@Service
class ServiceConfigurationService(private val serviceConfigurationRepository: ServiceConfigurationRepository) {

  fun getSelectedServices(subjectAccessRequest: SubjectAccessRequest): List<DpsService> {
    return subjectAccessRequest.services
      .split(",")
      .filter { it.isNotBlank() }
      .map { serviceName ->
        serviceConfigurationRepository.findByServiceName(serviceName.trim())?.let {
          DpsService(
            name = it.serviceName,
            businessName = it.label,
            url = it.url,
            orderPosition = it.order,
            content = null,
          )
        } ?: throw serviceNameNotFoundException(
          subjectAccessRequest,
          serviceName,
        )
      }.sortedBy { it.orderPosition }
  }

  private fun serviceNameNotFoundException(
    subjectAccessRequest: SubjectAccessRequest,
    serviceName: String,
  ): SubjectAccessRequestException {
    throw FatalSubjectAccessRequestException(
      message = "service with name '$serviceName' not found",
      event = ProcessingEvent.GET_SERVICE_CONFIGURATION,
      subjectAccessRequest = subjectAccessRequest,
      params = mapOf("serviceName" to serviceName),
    )
  }

}
