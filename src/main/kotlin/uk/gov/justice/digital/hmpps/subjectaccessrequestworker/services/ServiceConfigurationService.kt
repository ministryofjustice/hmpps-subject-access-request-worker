package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository
import java.util.UUID

@Service
class ServiceConfigurationService(
  private val serviceConfigurationRepository: ServiceConfigurationRepository,
  @param:Value("\${G1-api.url}") private val g1ApiUrl: String,
  @param:Value("\${G2-api.url}") private val g2ApiUrl: String,
  @param:Value("\${G3-api.url}") private val g3ApiUrl: String,
) {

  fun deleteAll() = serviceConfigurationRepository.deleteAll()

  @Transactional
  fun disableService(id: UUID) = serviceConfigurationRepository.updateEnabledById(id, false)

  fun save(
    serviceConfiguration: ServiceConfiguration,
  ) = serviceConfigurationRepository.saveAndFlush(serviceConfiguration)

  fun saveAll(
    serviceConfigurations: List<ServiceConfiguration>,
  ) = serviceConfigurationRepository.saveAllAndFlush(serviceConfigurations)

  fun getAllServiceConfigurations(): List<ServiceConfiguration> = serviceConfigurationRepository.findAll()

  fun findByServiceName(
    serviceName: String,
  ): ServiceConfiguration? = serviceConfigurationRepository.findByServiceName(serviceName)

  fun resolveUrlPlaceHolder(serviceConfiguration: ServiceConfiguration): String {
    val apiUrl = when (serviceConfiguration.serviceName) {
      "G1" -> g1ApiUrl
      "G2" -> g2ApiUrl
      "G3" -> g3ApiUrl
      else -> serviceConfiguration.url
    }
    return apiUrl
  }
}
