package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration

@DataJpaTest
class BacklogRequestRepositoryTest @Autowired constructor(
  val backlogRequestRepository: BacklogRequestRepository,
  val serviceConfigurationRepository: ServiceConfigurationRepository,
) {

  private val keyworkerApiServiceConfig = ServiceConfiguration(
    serviceName = "keyworker-api",
    label = "Keyworker",
    url = "",
    order = 1,
    enabled = false,
  )
  private val offenderCaseNotesServiceConfig = ServiceConfiguration(
    serviceName = "offender-case-notes",
    label = "offender-case-notes",
    url = "",
    order = 2,
    enabled = false,
  )
  private val courtCaseServiceServiceConfig = ServiceConfiguration(
    serviceName = "court-case-service",
    label = "court-case-service",
    url = "",
    order = 3,
    enabled = false,
  )

  private val serviceConfigurations = listOf(
    keyworkerApiServiceConfig,
    offenderCaseNotesServiceConfig,
    courtCaseServiceServiceConfig,
  )

  @BeforeEach
  fun setup() {
    backlogRequestRepository.deleteAll()
    serviceConfigurationRepository.saveAll(serviceConfigurations)
  }

  @Nested
  inner class SaveTestCases {

    @Test
    fun `should insert new request`() {
      val backlogRequest = backlogRequestRepository.save(BacklogRequest())
      val actual = backlogRequestRepository.findAll()

      assertThat(actual).hasSize(1)
      assertThat(actual.first()).isEqualTo(backlogRequest)
    }
  }
}
