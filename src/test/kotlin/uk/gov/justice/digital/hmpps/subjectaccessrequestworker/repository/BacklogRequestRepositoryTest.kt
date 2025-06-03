package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.PENDING
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

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

  @Nested
  inner class PendingServiceSummary {

    @Test
    fun `should return all service names for backlogRequest with no service summary entries`() {
      val backlogRequest = backlogRequestRepository.save(BacklogRequest())

      val actual = backlogRequestRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)

      assertThat(actual.size).isEqualTo(3)
      assertThat(actual).containsExactly(
        keyworkerApiServiceConfig,
        offenderCaseNotesServiceConfig,
        courtCaseServiceServiceConfig,
      )
    }

    @Test
    fun `should return service names for backlog request that do not have a COMPLETE service summary entry`() {
      val backlogRequest = BacklogRequest()
      val services = mutableListOf(
        ServiceSummary(
          serviceName = "keyworker-api",
          backlogRequest = backlogRequest,
          status = COMPLETE,
        ),
      )
      backlogRequest.serviceSummary = services

      backlogRequestRepository.save(backlogRequest)
      val actual = backlogRequestRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)
      assertThat(actual.size).isEqualTo(2)
      assertThat(actual).containsExactly(
        offenderCaseNotesServiceConfig,
        courtCaseServiceServiceConfig,
      )
    }

    @Test
    fun `should return all service names for backlog request when entries has status PENDING`() {
      val backlogRequest = BacklogRequest()
      backlogRequest.serviceSummary = mutableListOf(
        ServiceSummary(
          serviceName = "keyworker-api",
          backlogRequest = backlogRequest,
          status = PENDING,
        ),
        ServiceSummary(
          serviceName = "offender-case-notes",
          backlogRequest = backlogRequest,
          status = PENDING,
        ),
        ServiceSummary(
          serviceName = "court-case-service",
          backlogRequest = backlogRequest,
          status = PENDING,
        ),
      )

      backlogRequestRepository.save(backlogRequest)
      val actual = backlogRequestRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)
      assertThat(actual.size).isEqualTo(3)
      assertThat(actual).containsExactlyElementsOf(serviceConfigurations)
    }

    @Test
    fun `should return empty when backlog request has COMPLETE service summary entry for each service`() {
      val backlogRequest = BacklogRequest()
      backlogRequest.serviceSummary = mutableListOf(
        ServiceSummary(
          serviceName = "keyworker-api",
          backlogRequest = backlogRequest,
          status = COMPLETE,
        ),
        ServiceSummary(
          serviceName = "offender-case-notes",
          backlogRequest = backlogRequest,
          status = COMPLETE,
        ),
        ServiceSummary(
          serviceName = "court-case-service",
          backlogRequest = backlogRequest,
          status = COMPLETE,
        ),
      )

      backlogRequestRepository.save(backlogRequest)
      val actual = backlogRequestRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)
      assertThat(actual).isEmpty()
    }
  }

  @Nested
  inner class GetNextToProcessTestCases {

    private val backOffThreshold = now().minusMinutes(30)

    @Test
    fun `should return pending request created first`() {
      val request1 = backlogRequestRepository.save(
        BacklogRequest(
          createdAt = now().minusDays(1),
          status = PENDING,
        ),
      )
      backlogRequestRepository.save(
        BacklogRequest(
          createdAt = now().minusHours(1),
          status = PENDING,
        ),
      )

      assertThat(backlogRequestRepository.count()).isEqualTo(2)

      val actual = backlogRequestRepository.getNextToProcess(backOffThreshold)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(request1)
    }

    @Test
    fun `should return null if pending request claim date is within backOffThreshold`() {
      backlogRequestRepository.save(
        BacklogRequest(
          createdAt = now().minusDays(1),
          status = PENDING,
          claimDateTime = now().minusMinutes(10),
        ),
      )

      val actual = backlogRequestRepository.getNextToProcess(backOffThreshold)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null if no pending requests are available`() {
      backlogRequestRepository.save(
        BacklogRequest(
          createdAt = now().minusDays(1),
          status = COMPLETE,
          claimDateTime = now().minusMinutes(10),
        ),
      )

      val actual = backlogRequestRepository.getNextToProcess(backOffThreshold)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return 2nd oldest request if 1st oldest request is within backOffThreshold `() {
      // Request 1 created first but is within the backOff period.
      backlogRequestRepository.save(
        BacklogRequest(
          createdAt = now().minusDays(2),
          status = PENDING,
          claimDateTime = now().minusMinutes(15),
        ),
      )

      val request2 = backlogRequestRepository.save(
        BacklogRequest(
          createdAt = now().minusDays(1),
          status = PENDING,
          claimDateTime = now().minusMinutes(40),
        ),
      )

      val actual = backlogRequestRepository.getNextToProcess(backOffThreshold)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(request2)
    }
  }

  @Nested
  inner class GetByIdAndStatusTestCases {

    @Test
    fun `should return null when no request is found`() {
      assertThat(backlogRequestRepository.findByIdAndStatus(UUID.randomUUID(), PENDING)).isNull()
    }

    @Test
    fun `should return expected request when it exists`() {
      val expected = backlogRequestRepository.save(BacklogRequest(status = PENDING))

      val actual = backlogRequestRepository.findByIdAndStatus(expected.id, PENDING)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(expected)
    }
  }

  @Nested
  inner class ClaimRequestTestCases {
    @Test
    fun `should claim request if claimDateTime is null`() {
      val expected = backlogRequestRepository.save(BacklogRequest(status = PENDING, claimDateTime = null))

      val beforeSave = now()
      val result = backlogRequestRepository.claimRequest(expected.id)
      assertThat(result).isEqualTo(1)

      assertClaimSuccessful(expected.id, beforeSave)
    }

    @Test
    fun `should claim request if claimDateTime is before backoffThreshold`() {
      val expected = backlogRequestRepository.save(
        BacklogRequest(
          status = PENDING,
          claimDateTime = now().minusMinutes(35),
        ),
      )

      val beforeSave = now()
      val result = backlogRequestRepository.claimRequest(expected.id, backOffThreshold)
      assertThat(result).isEqualTo(1)
      assertClaimSuccessful(expected.id, beforeSave)
    }

    @Test
    fun `should not claim request if claimDateTime is within backoffThreshold`() {
      val expected = backlogRequestRepository.save(
        BacklogRequest(
          status = PENDING,
          claimDateTime = now().minusMinutes(20),
        ),
      )

      val result = backlogRequestRepository.claimRequest(expected.id, backOffThreshold)
      assertThat(result).isEqualTo(0)
      assertClaimUnsuccessful(expected.id, PENDING)
    }

    @Test
    fun `should not claim request if claimDateTime is within backoffThreshold and status is COMPLETE`() {
      val expected = backlogRequestRepository.save(
        BacklogRequest(
          status = COMPLETE,
          claimDateTime = now().minusMinutes(20),
        ),
      )

      val result = backlogRequestRepository.claimRequest(expected.id, backOffThreshold)
      assertThat(result).isEqualTo(0)
      assertClaimUnsuccessful(expected.id, COMPLETE)
    }

    @Test
    fun `should not claim request if claimDateTime is null and status is COMPLETE`() {
      val expected = backlogRequestRepository.save(
        BacklogRequest(
          status = COMPLETE,
          claimDateTime = null,
        ),
      )

      val result = backlogRequestRepository.claimRequest(expected.id, backOffThreshold)
      assertThat(result).isEqualTo(0)
      val actual = backlogRequestRepository.findByIdOrNull(expected.id)
      assertThat(actual).isNotNull
      assertThat(actual!!.status).isEqualTo(COMPLETE)
    }

    private fun assertClaimSuccessful(id: UUID, beforeSave: LocalDateTime) {
      val actual = backlogRequestRepository.findByIdOrNull(id)
      assertThat(actual).isNotNull
      assertThat(actual!!.status).isEqualTo(PENDING)
      assertThat(actual.claimDateTime).isNotNull()
      assertThat(actual.claimDateTime).isBetween(beforeSave, now())
    }

    private fun assertClaimUnsuccessful(id: UUID, expectedStatus: BacklogRequestStatus) {
      val actual = backlogRequestRepository.findByIdOrNull(id)
      assertThat(actual).isNotNull
      assertThat(actual!!.status).isEqualTo(expectedStatus)
      assertThat(actual.claimDateTime).isNotNull()
    }
  }

  companion object {
    private val backOffThreshold = now().minusMinutes(30)
  }
}
