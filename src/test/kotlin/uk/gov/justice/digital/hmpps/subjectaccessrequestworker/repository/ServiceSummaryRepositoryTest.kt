package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.COMPLETE
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus.PENDING
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary

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
    order = 1,
    enabled = true,
  )
  private val offenderCaseNotesServiceConfig = ServiceConfiguration(
    serviceName = "offender-case-notes",
    label = "offender-case-notes",
    url = "",
    order = 2,
    enabled = true,
  )
  private val courtCaseServiceServiceConfig = ServiceConfiguration(
    serviceName = "court-case-service",
    label = "court-case-service",
    url = "",
    order = 3,
    enabled = true,
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

      val actual = serviceSummaryRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)

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

      val actual = serviceSummaryRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)
      assertThat(actual.size).isEqualTo(serviceConfigurations.size)
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

      val actual = serviceSummaryRepository.getPendingServiceSummariesForRequestId(backlogRequest.id)
      assertThat(actual).isEmpty()
    }
  }

  @Nested
  inner class ExistsByBacklogRequestIdAndServiceNameTestCases {

    @Test
    fun `should return true if combination exists`() {
      val req = BacklogRequest().addServiceSummaries()
      req.addServiceSummaries(
        ServiceSummary(serviceName = "service1", backlogRequest = req),
      )
      backlogRequestRepository.saveAndFlush(req)

      assertThat(serviceSummaryRepository.existsByBacklogRequestIdAndServiceName(req.id, "service1")).isTrue()
    }

    @Test
    fun `should return false if combination does not exist`() {
      val req = BacklogRequest().addServiceSummaries()
      req.addServiceSummaries(
        ServiceSummary(serviceName = "service1", backlogRequest = req),
      )
      backlogRequestRepository.saveAndFlush(req)

      assertThat(serviceSummaryRepository.existsByBacklogRequestIdAndServiceName(req.id, "service2")).isFalse()
    }
  }

  @Nested
  inner class FindOneByBacklogRequestIdAndServiceNameTestCases {

    @Test
    fun `should return null if backlogRequest does not exist`() {
      val actual = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceName(
        backlogRequestId = BacklogRequest().id,
        serviceName = "someService",
      )
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null if backlogRequest exists with no service summaries`() {
      val backlogRequest = backlogRequestRepository.saveAndFlush(BacklogRequest())
      val actual = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceName(
        backlogRequestId = backlogRequest.id,
        serviceName = "someService",
      )
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null if backlogRequest exists but does not have a service summary for the requested service name`() {
      val backlogRequest = backlogRequestRepository.saveAndFlush(
        BacklogRequest().addServiceSummaries(ServiceSummary(serviceName = "serviceAbc")),
      )

      val actual = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceName(
        backlogRequestId = backlogRequest.id,
        serviceName = "serviceXyz",
      )
      assertThat(actual).isNull()
    }

    @Test
    fun `should return service summary if exists`() {
      val backlogRequest = backlogRequestRepository.saveAndFlush(
        BacklogRequest().addServiceSummaries(ServiceSummary(serviceName = "serviceAbc")),
      )

      val actual = serviceSummaryRepository.findOneByBacklogRequestIdAndServiceName(
        backlogRequestId = backlogRequest.id,
        serviceName = "serviceAbc",
      )
      assertThat(actual).isNotNull
      assertThat(actual!!.serviceName).isEqualTo("serviceAbc")
      assertThat(actual.backlogRequest).isNotNull
      assertThat(actual.backlogRequest!!.id).isEqualTo(backlogRequest.id)
    }
  }
}
