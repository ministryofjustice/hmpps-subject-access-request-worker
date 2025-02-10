package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository

@Service
class ServiceConfigurationService(
  private val serviceConfigurationRepository: ServiceConfigurationRepository,
  @Value("\${G1-api.url}") private val g1ApiUrl: String,
  @Value("\${G2-api.url}") private val g2ApiUrl: String,
  @Value("\${G3-api.url}") private val g3ApiUrl: String,
) {

  fun getSelectedServices(subjectAccessRequest: SubjectAccessRequest): List<DpsService> {
    return subjectAccessRequest.services
      .split(",")
      .filter { it.isNotBlank() }
      .map { serviceName ->
        serviceConfigurationRepository.findByServiceName(serviceName.trim())?.let {
          DpsService(
            name = it.serviceName,
            businessName = it.label,
            url = resolveUrlPlaceHolder(it),
            orderPosition = it.order,
            content = null,
          )
        } ?: throw serviceNameNotFoundException(
          subjectAccessRequest,
          serviceName,
        )
      }.sortedBy { it.orderPosition }
  }

  private fun resolveUrlPlaceHolder(serviceConfiguration: ServiceConfiguration) =
    when (serviceConfiguration.serviceName) {
      "G1" -> g1ApiUrl
      "G2" -> g2ApiUrl
      "G3" -> g3ApiUrl
      else -> serviceConfiguration.url
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
