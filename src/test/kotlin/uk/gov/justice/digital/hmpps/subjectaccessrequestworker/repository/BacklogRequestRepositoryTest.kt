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

  companion object {
    private val backOffThreshold = now().minusMinutes(30)
  }

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

    @Test
    fun `should persist child service summaries`() {
      val request = BacklogRequest()
      val serviceSummary1 = ServiceSummary(
        id = UUID.randomUUID(),
        backlogRequest = request,
        serviceConfiguration = keyworkerApiServiceConfig,
        dataHeld = true,
        status = COMPLETE,
      )

      val serviceSummary2 = ServiceSummary(
        id = UUID.randomUUID(),
        backlogRequest = request,
        serviceConfiguration = offenderCaseNotesServiceConfig,
        dataHeld = true,
        status = COMPLETE,
      )
      request.addServiceSummaries(serviceSummary1, serviceSummary2)

      backlogRequestRepository.saveAndFlush(request)
      val actual = backlogRequestRepository.findByIdOrNull(request.id)
      assertThat(actual).isNotNull

      assertThat(actual!!.id).isEqualTo(request.id)
      assertThat(actual.sarCaseReferenceNumber).isEqualTo(request.sarCaseReferenceNumber)
      assertThat(actual.nomisId).isEqualTo(request.nomisId)
      assertThat(actual.ndeliusCaseReferenceId).isEqualTo(request.ndeliusCaseReferenceId)
      assertThat(actual.status).isEqualTo(request.status)
      assertThat(actual.dateFrom).isEqualTo(request.dateFrom)
      assertThat(actual.dateTo).isEqualTo(request.dateTo)
      assertThat(actual.claimDateTime).isEqualTo(request.claimDateTime)
      assertThat(actual.createdAt).isEqualTo(request.createdAt)
      assertThat(actual.completedAt).isEqualTo(request.completedAt)
      assertThat(actual.serviceSummary).containsExactlyElementsOf(listOf(serviceSummary1, serviceSummary2))
    }
  }

  @Nested
  inner class GetVersionsTestCases {

    @Test
    fun `should return empty list when no versions exist`() {
      assertThat(backlogRequestRepository.findDistinctVersions()).isEmpty()
    }

    @Test
    fun `should return expected versions`() {
      backlogRequestRepository.save(
        BacklogRequest(
          version = "1.0",
          createdAt = now().minusDays(1),
          status = PENDING,
        ),
      )
      backlogRequestRepository.save(
        BacklogRequest(
          version = "2.0",
          createdAt = now().minusHours(1),
          status = PENDING,
        ),
      )
      assertThat(backlogRequestRepository.findDistinctVersions()).isEqualTo(setOf("1.0", "2.0"))
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
      val result = backlogRequestRepository.updateClaimDateTime(expected.id, now().minusMinutes(5))
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
      val result = backlogRequestRepository.updateClaimDateTime(expected.id, backOffThreshold)
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

      val result = backlogRequestRepository.updateClaimDateTime(expected.id, backOffThreshold)
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

      val result = backlogRequestRepository.updateClaimDateTime(expected.id, backOffThreshold)
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

      val result = backlogRequestRepository.updateClaimDateTime(expected.id, backOffThreshold)
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

  @Nested
  inner class FindCompleteRequestOrNullTestCases {

    @Test
    fun `should return request when service summary with status COMPLETE exists for each service in service configuration`() {
      val request = BacklogRequest()

      serviceConfigurations.forEach {
        request.addServiceSummary(
          ServiceSummary(
            id = UUID.randomUUID(),
            backlogRequest = request,
            serviceConfiguration = it,
            dataHeld = true,
            status = COMPLETE,
          ),
        )
      }
      backlogRequestRepository.save(request)

      val actual = backlogRequestRepository.findCompleteRequestOrNull(request.id)
      assertThat(actual).isNotNull
      assertThat(actual!!.serviceSummary).hasSize(3)
    }

    @Test
    fun `should return null no service summaries exist`() {
      val request = backlogRequestRepository.save(BacklogRequest())

      val actual = backlogRequestRepository.findCompleteRequestOrNull(request.id)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null when all service summaries exist with status PENDING`() {
      val request = BacklogRequest()

      serviceConfigurations.forEach {
        request.addServiceSummary(
          ServiceSummary(
            id = UUID.randomUUID(),
            backlogRequest = request,
            serviceConfiguration = it,
            dataHeld = true,
            status = PENDING,
          ),
        )
      }
      backlogRequestRepository.save(request)

      val actual = backlogRequestRepository.findCompleteRequestOrNull(request.id)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null when a service summary with status COMPLETE does not exist for all service configurations `() {
      val request = BacklogRequest()

      // Add 2/3 service summaries with status complete
      request.addServiceSummaries(
        ServiceSummary(
          id = UUID.randomUUID(),
          backlogRequest = request,
          serviceConfiguration = serviceConfigurations[0],
          dataHeld = true,
          status = COMPLETE,
        ),
        ServiceSummary(
          id = UUID.randomUUID(),
          backlogRequest = request,
          serviceConfiguration = serviceConfigurations[1],
          dataHeld = true,
          status = COMPLETE,
        ),
      )
      backlogRequestRepository.save(request)

      val actual = backlogRequestRepository.findCompleteRequestOrNull(request.id)
      assertThat(actual).isNull()
    }
  }

  @Nested
  inner class FindDataHeldByIdOrNullTestCases {

    @Test
    fun `should return null when backlogRequest does not exist`() {
      assertThat(backlogRequestRepository.findDataHeldByIdOrNull(UUID.randomUUID())).isNull()
    }

    @Test
    fun `should return null when no service summary exists for backlogRequest`() {
      val request = backlogRequestRepository.save(BacklogRequest())
      assertThat(request).isNotNull

      assertThat(backlogRequestRepository.findDataHeldByIdOrNull(request.id)).isNull()
    }

    @Test
    fun `should return null when dateHeld is FALSE for all service summaries`() {
      val request = BacklogRequest()

      serviceConfigurations.forEach {
        request.addServiceSummary(
          ServiceSummary(
            id = UUID.randomUUID(),
            backlogRequest = request,
            serviceConfiguration = it,
            dataHeld = false,
            status = PENDING,
          ),
        )
      }
      backlogRequestRepository.save(request)

      assertThat(backlogRequestRepository.findDataHeldByIdOrNull(request.id)).isNull()
    }

    @Test
    fun `should return backlogRequest when dataHeld is TRUE for all service summaries`() {
      val request = BacklogRequest()

      serviceConfigurations.forEach {
        request.addServiceSummary(
          ServiceSummary(
            id = UUID.randomUUID(),
            backlogRequest = request,
            serviceConfiguration = it,
            dataHeld = true,
            status = COMPLETE,
          ),
        )
      }
      val savedRequest = backlogRequestRepository.save(request)

      val actual = backlogRequestRepository.findDataHeldByIdOrNull(request.id)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(savedRequest)
    }

    @Test
    fun `should return backlogRequest when dataHeld is TRUE at least one service summary`() {
      val request = BacklogRequest()

      serviceConfigurations.forEachIndexed { i, service ->
        request.addServiceSummary(
          ServiceSummary(
            id = UUID.randomUUID(),
            backlogRequest = request,
            serviceConfiguration = service,
            dataHeld = (i == 0),
            status = COMPLETE,
          ),
        )
      }
      val savedRequest = backlogRequestRepository.save(request)

      val actual = backlogRequestRepository.findDataHeldByIdOrNull(request.id)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(savedRequest)
    }

    @Test
    fun `should return null when dataHeld is TRUE on at least one service summary but status is not COMPLETE`() {
      val request = BacklogRequest()

      serviceConfigurations.forEachIndexed { i, service ->
        request.addServiceSummary(
          ServiceSummary(
            id = UUID.randomUUID(),
            backlogRequest = request,
            serviceConfiguration = service,
            dataHeld = true,
            status = PENDING,
          ),
        )
      }
      backlogRequestRepository.save(request)

      assertThat(backlogRequestRepository.findDataHeldByIdOrNull(request.id)).isNull()
    }
  }
}
