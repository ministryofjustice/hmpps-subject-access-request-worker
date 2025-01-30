package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration

class ServiceConfigurationRepositoryTest : IntegrationTestBase() {

  @Autowired
  private lateinit var repository: ServiceConfigurationRepository

  @BeforeEach
  fun setup() {
    repository.deleteAll()
  }

  @Test
  fun `find all should return all service configurations`() {
    repository.save(serviceConfig1)

    val actual: List<ServiceConfiguration> = repository.findAll()

    assertThat(actual).isNotNull
    assertThat(actual.size).isEqualTo(1)
    assertThat(actual[0]).isEqualTo(serviceConfig1)
  }

  @Test
  fun `find by service name should return the expected service configuration`() {
    repository.save(serviceConfig1)

    val actual: ServiceConfiguration? = repository.findByServiceName(serviceConfig1.serviceName)

    assertThat(actual).isNotNull
    assertThat(actual).isEqualTo(serviceConfig1)
  }

  companion object {

    val serviceConfig1 = ServiceConfiguration(
      serviceName = "AAA",
      label = "AAA",
      url = "http://localhost:1234",
      order = 1,
      enabled = true,
    )
  }
}