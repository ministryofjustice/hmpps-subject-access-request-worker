package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.PENDING
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import java.util.UUID

@DataJpaTest
class ServiceSummaryRepositoryTest @Autowired constructor(
  val serviceSummaryRepository: ServiceSummaryRepository,
  val backlogRequestRepository: BacklogRequestRepository,
  val serviceConfigurationRepository: ServiceConfigurationRepository,
) {

  private val keyworkerApiServiceConfig = ServiceConfiguration(
    serviceName = "keyworker-api",
    label = "Keyworker",
    url = "",
    enabled = true,
    category = ServiceCategory.PRISON,
  )
  private val offenderCaseNotesServiceConfig = ServiceConfiguration(
    serviceName = "offender-case-notes",
    label = "offender-case-notes",
    url = "",
    enabled = true,
    category = ServiceCategory.PRISON,
  )
  private val courtCaseServiceServiceConfig = ServiceConfiguration(
    serviceName = "court-case-service",
    label = "court-case-service",
    url = "",
    enabled = true,
    category = ServiceCategory.PROBATION,
  )
  private val serviceConfigurations =
    listOf(keyworkerApiServiceConfig, offenderCaseNotesServiceConfig, courtCaseServiceServiceConfig)

  @BeforeEach
  fun setup() {
    backlogRequestRepository.deleteAll()
    serviceSummaryRepository.deleteAll()
    serviceConfigurationRepository.saveAll(serviceConfigurations)
  }

  @Nested
  inner class PendingServiceSummary {

    @Test
    fun `should return all service names for backlogRequest with no service summary entries`() {
      val backlogRequest = backlogRequestRepository.save(BacklogRequest())

      val actual = serviceSummaryRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)

      assertThat(actual.size).isEqualTo(serviceConfigurations.size)
      assertThat(actual).containsExactlyInAnyOrder(
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
          serviceConfiguration = keyworkerApiServiceConfig,
          backlogRequest = backlogRequest,
          status = COMPLETE,
        ),
      )
      backlogRequest.serviceSummary = services
      backlogRequestRepository.save(backlogRequest)

      val actual = serviceSummaryRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)

      assertThat(actual.size).isEqualTo(2)
      assertThat(actual).containsExactly(
        courtCaseServiceServiceConfig,
        offenderCaseNotesServiceConfig,
      )
    }

    @Test
    fun `should return all service names for backlog request when entries has status PENDING`() {
      val backlogRequest = BacklogRequest()
      backlogRequest.serviceSummary = mutableListOf(
        ServiceSummary(
          serviceConfiguration = keyworkerApiServiceConfig,
          backlogRequest = backlogRequest,
          status = PENDING,
        ),
        ServiceSummary(
          serviceConfiguration = offenderCaseNotesServiceConfig,
          backlogRequest = backlogRequest,
          status = PENDING,
        ),
        ServiceSummary(
          serviceConfiguration = courtCaseServiceServiceConfig,
          backlogRequest = backlogRequest,
          status = PENDING,
        ),
      )

      backlogRequestRepository.save(backlogRequest)

      val actual = serviceSummaryRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)
      assertThat(actual.size).isEqualTo(serviceConfigurations.size)
      assertThat(actual).containsExactlyInAnyOrderElementsOf(serviceConfigurations)
    }

    @Test
    fun `should return empty when backlog request has COMPLETE service summary entry for each service`() {
      val backlogRequest = BacklogRequest()
      backlogRequest.serviceSummary = mutableListOf(
        ServiceSummary(
          serviceConfiguration = keyworkerApiServiceConfig,
          backlogRequest = backlogRequest,
          status = COMPLETE,
        ),
        ServiceSummary(
          serviceConfiguration = offenderCaseNotesServiceConfig,
          backlogRequest = backlogRequest,
          status = COMPLETE,
        ),
        ServiceSummary(
          serviceConfiguration = courtCaseServiceServiceConfig,
          backlogRequest = backlogRequest,
          status = COMPLETE,
        ),
      )
      backlogRequestRepository.save(backlogRequest)

      val actual = serviceSummaryRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)
      assertThat(actual).isEmpty()
    }

    @Test
    fun `should only return service names for backlogRequest with no service summary entries that are enabled`() {
      val backlogRequest = backlogRequestRepository.save(BacklogRequest())

      val updateCount = serviceConfigurationRepository.updateEnabledById(keyworkerApiServiceConfig.id, false)
      assertThat(updateCount).isEqualTo(1)

      val actual = serviceSummaryRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)

      assertThat(actual.size).isEqualTo(serviceConfigurations.size - 1)
      assertThat(actual).containsExactlyInAnyOrder(
        offenderCaseNotesServiceConfig,
        courtCaseServiceServiceConfig,
      )
    }
  }

  @Nested
  inner class ExistsByBacklogRequestIdAndServiceNameTestCases {

    @Test
    fun `should return true if combination exists`() {
      val req = BacklogRequest().addServiceSummaries()
      req.addServiceSummaries(
        ServiceSummary(serviceConfiguration = keyworkerApiServiceConfig, backlogRequest = req),
      )
      backlogRequestRepository.saveAndFlush(req)

      assertThat(
        serviceSummaryRepository.existsByBacklogRequestIdAndServiceConfigurationId(
          req.id,
          keyworkerApiServiceConfig.id,
        ),
      ).isTrue()
    }

    @Test
    fun `should return false if combination does not exist`() {
      val req = BacklogRequest().addServiceSummaries()
      req.addServiceSummaries(
        ServiceSummary(serviceConfiguration = keyworkerApiServiceConfig, backlogRequest = req),
      )
      backlogRequestRepository.saveAndFlush(req)

      assertThat(
        serviceSummaryRepository.existsByBacklogRequestIdAndServiceConfigurationId(
          req.id,
          ServiceConfiguration().id,
        ),
      ).isFalse()
    }
  }

  @Nested
  inner class FindOneByBacklogRequestIdAndServiceConfigurationTestCases {

    @Test
    fun `should return null if backlogRequest does not exist`() {
      val actual = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceConfigurationId(
        backlogRequestId = BacklogRequest().id,
        serviceConfigurationId = UUID.randomUUID(),
      )
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null if backlogRequest exists with no service summaries`() {
      val backlogRequest = backlogRequestRepository.saveAndFlush(BacklogRequest())
      val actual = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceConfigurationId(
        backlogRequestId = backlogRequest.id,
        serviceConfigurationId = keyworkerApiServiceConfig.id,
      )
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null if backlogRequest exists but does not have a service summary for the requested service name`() {
      val backlogRequest = backlogRequestRepository.saveAndFlush(
        BacklogRequest().addServiceSummaries(ServiceSummary(serviceConfiguration = keyworkerApiServiceConfig)),
      )

      val actual = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceConfigurationId(
        backlogRequestId = backlogRequest.id,
        serviceConfigurationId = courtCaseServiceServiceConfig.id,
      )
      assertThat(actual).isNull()
    }

    @Test
    fun `should return service summary if exists`() {
      val backlogRequest = backlogRequestRepository.saveAndFlush(
        BacklogRequest().addServiceSummaries(ServiceSummary(serviceConfiguration = courtCaseServiceServiceConfig)),
      )

      val actual = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceConfigurationId(
        backlogRequestId = backlogRequest.id,
        serviceConfigurationId = courtCaseServiceServiceConfig.id,
      )
      assertThat(actual).isNotNull
      assertThat(actual!!.serviceConfiguration?.id).isEqualTo(courtCaseServiceServiceConfig.id)
      assertThat(actual.backlogRequest).isNotNull
      assertThat(actual.backlogRequest!!.id).isEqualTo(backlogRequest.id)
    }
  }

  @Nested
  inner class CountByBacklogRequestIdAndDataHeldTestCases {

    @Test
    fun `should return 0 when no backlogRequest found`() {
      assertThat(serviceSummaryRepository.countByBacklogRequestIdAndDataHeld(UUID.randomUUID())).isEqualTo(0)
    }

    @Test
    fun `should return 0 when no backlogRequest has no service summaries`() {
      val backlogRequest = backlogRequestRepository.saveAndFlush(BacklogRequest())
      assertThat(serviceSummaryRepository.countByBacklogRequestIdAndDataHeld(backlogRequest.id)).isEqualTo(0)
    }

    @ParameterizedTest
    @CsvSource(
      value = [
        "data held is false for all service summaries | false | false | false | 0",
        "data held is true for one service summary | false | true | false | 1",
        "data held is true for two service summaries | true | true | false | 2",
        "data held is true for three service summaries | true | true | true | 3",
      ],
      delimiterString = "|",
    )
    fun `should return expected count when service summaries exist for backlog request`(
      description: String,
      service1DataHeld: Boolean,
      service2DataHeld: Boolean,
      service3DataHeld: Boolean,
      expectedResult: Long,
    ) {
      val backlogRequest = backlogRequestRepository.saveAndFlush(
        BacklogRequest()
          .addServiceSummaries(
            ServiceSummary(serviceConfiguration = keyworkerApiServiceConfig, dataHeld = service1DataHeld),
            ServiceSummary(serviceConfiguration = offenderCaseNotesServiceConfig, dataHeld = service2DataHeld),
            ServiceSummary(serviceConfiguration = courtCaseServiceServiceConfig, dataHeld = service3DataHeld),
          ),
      )
      assertThat(backlogRequest.serviceSummary).hasSize(3)

      val actual = serviceSummaryRepository.countByBacklogRequestIdAndDataHeld(backlogRequest.id)
      assertThat(actual).isEqualTo(expectedResult)
    }
  }
}
