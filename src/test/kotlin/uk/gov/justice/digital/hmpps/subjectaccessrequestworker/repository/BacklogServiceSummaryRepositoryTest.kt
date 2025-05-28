package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration

@DataJpaTest
class BacklogServiceSummaryRepositoryTest @Autowired constructor(
  val serviceSummaryRepository: BacklogServiceSummaryRepository,
  val backlogRequestRepository: BacklogRequestRepository,
  val serviceConfigurationRepository: ServiceConfigurationRepository,
) {

  private val backlogRequest = BacklogRequest()
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
    serviceSummaryRepository.deleteAll()
    backlogRequestRepository.deleteAll()
    serviceConfigurationRepository.deleteAll()

    serviceConfigurationRepository.saveAll(serviceConfigurations)
    backlogRequestRepository.save(backlogRequest)
  }

  @Test
  fun `should return all services that do not have a service summary entry`() {
    val actual = serviceSummaryRepository.getPendingServiceSummaries(backlogRequest.id)
    assertThat(actual.size).isEqualTo(3)
    assertThat(actual).containsExactly("keyworker-api", "offender-case-notes", "court-case-service")
  }

  @Test
  fun `should return only services that do not have a service summary entry with status COMPLETE`() {
    val request = BacklogRequest(
      serviceSummary = listOf(
        BacklogRequestServiceSummary(
          serviceName = serviceConfigurations[0].serviceName,
          status = BacklogRequestStatus.PENDING,
        ),
        BacklogRequestServiceSummary(
          serviceName = serviceConfigurations[1].serviceName,
          status = BacklogRequestStatus.COMPLETE,
        ),
        BacklogRequestServiceSummary(
          serviceName = serviceConfigurations[2].serviceName,
          status = BacklogRequestStatus.PENDING,
        ),
      ),
    )

    backlogRequestRepository.save(request)

    val actual = serviceSummaryRepository.getPendingServiceSummaries(backlogRequest.id)
    assertThat(actual.size).isEqualTo(2)
    assertThat(actual).containsExactly("keyworker-api", "court-case-service")
  }
}