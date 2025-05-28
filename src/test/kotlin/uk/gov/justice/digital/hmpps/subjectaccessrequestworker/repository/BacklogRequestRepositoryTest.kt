package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration

@DataJpaTest
class BacklogRequestRepositoryTest @Autowired constructor(
  val backlogRequestRepository: BacklogRequestRepository,
  val serviceConfigurationRepository: ServiceConfigurationRepository,
  val serviceSummaryRepository: BacklogServiceSummaryRepository,
) {

  private val serviceConfigurations = listOf(
    ServiceConfiguration(
      serviceName = "keyworker-api",
      label = "Keyworker",
      url = "",
      order = 1,
      enabled = false,
    ),
    ServiceConfiguration(
      serviceName = "offender-case-notes",
      label = "offender-case-notes",
      url = "",
      order = 2,
      enabled = false,
    ),
    ServiceConfiguration(
      serviceName = "court-case-service",
      label = "court-case-service",
      url = "",
      order = 3,
      enabled = false,
    ),
  )

  @BeforeEach
  fun setup() {
    backlogRequestRepository.deleteAll()
    serviceConfigurationRepository.saveAll(serviceConfigurations)
  }

  @Test
  fun `should insert new request`() {
    val backlogRequest = backlogRequestRepository.save(BacklogRequest())
    val actual = backlogRequestRepository.findAll()

    assertThat(actual).hasSize(1)
    assertThat(actual.first()).isEqualTo(backlogRequest)
  }

  @Nested
  inner class PendingServiceSummary {

    @Test
    fun `should return all service names for backlogRequest with no service summary entries`() {
      val backlogRequest = backlogRequestRepository.save(BacklogRequest())

      val actual = backlogRequestRepository.getPendingServiceSummaries(backlogRequest.id)

      assertThat(actual.size).isEqualTo(3)
      assertThat(actual).containsExactly("keyworker-api", "offender-case-notes", "court-case-service")
    }

    @Test
    fun `should return service names for backlog request that do not have a service summary entry`() {
      val backlogRequest = BacklogRequest(
        serviceSummary = listOf(
          BacklogRequestServiceSummary(
            serviceName = "keyworker-api",
            status = BacklogRequestStatus.COMPLETE,
          ),
          BacklogRequestServiceSummary(
            serviceName = "offender-case-notes",
            status = BacklogRequestStatus.COMPLETE,
          ),
          BacklogRequestServiceSummary(
            serviceName = "court-case-service",
            status = BacklogRequestStatus.COMPLETE,
          ),
        ),
      )

      backlogRequestRepository.saveAndFlush(backlogRequest)
      val actual = backlogRequestRepository.getPendingServiceSummaries(backlogRequest.id)
      val actual2 = serviceSummaryRepository.getPendingServiceSummaries(backlogRequest.id)

      assertThat(actual.size).isEqualTo(2)
      assertThat(actual).containsExactly("offender-case-notes", "court-case-service")
    }
  }
}
