package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceSummaryRepository

@DataJpaTest
class BacklogRequestServiceAddSummaryTest @Autowired constructor(
  private val backlogRequestRepository: BacklogRequestRepository,
  private val serviceSummaryRepository: ServiceSummaryRepository,
  private val serviceConfigurationRepository: ServiceConfigurationRepository,
) {
  private val dynamicServicesClient: DynamicServicesClient = mock()

  private val backlogRequestService = BacklogRequestService(
    backlogRequestRepository,
    serviceSummaryRepository,
    serviceConfigurationRepository,
    dynamicServicesClient,
  )

  private val serviceOneConfiguration = ServiceConfiguration(
    serviceName = "service1",
    label = "Service One",
    url = "http://localhost:1234",
    order = 1,
    enabled = true,
  )

  @BeforeEach
  fun beforeEach() {
    backlogRequestRepository.deleteAll()
    serviceConfigurationRepository.deleteAll()
    serviceSummaryRepository.deleteAll()
    serviceConfigurationRepository.save(serviceOneConfiguration)
  }

  @Test
  fun `should throw exception if serviceName is empty`() {
    val backlogRequest = BacklogRequest()
    val actual = assertThrows<BacklogRequestException> {
      backlogRequestService.addServiceSummary(backlogRequest, ServiceSummary())
    }

    assertThat(actual.message).isEqualTo("Service name cannot be empty")
    assertThat(actual.backlogRequestId).isEqualTo(backlogRequest.id)
  }

  @Test
  fun `should throw exception if serviceName does not exist in service configuration`() {
    val backlogRequest = BacklogRequest()
    val actual = assertThrows<BacklogRequestException> {
      backlogRequestService.addServiceSummary(backlogRequest, ServiceSummary(serviceName = "madeUpService"))
    }

    assertThat(actual.message).isEqualTo("Service Configuration does not exist for serviceName")
    assertThat(actual.backlogRequestId).isEqualTo(backlogRequest.id)
  }

  @Test
  fun `should update existing service summary if already exists`() {
    var backlogRequest = BacklogRequest()
    val summary1 = ServiceSummary(serviceName = "service1", status = BacklogRequestStatus.PENDING, dataHeld = false)
    backlogRequest.addServiceSummaries(summary1)

    backlogRequest = backlogRequestService.save(backlogRequest)
    assertThat(backlogRequest.serviceSummary).hasSize(1)

    // Update the service summary
    backlogRequestService.addServiceSummary(
      backlogRequest,
      ServiceSummary(
        serviceName = "service1",
        serviceOrder = 1,
        status = BacklogRequestStatus.COMPLETE,
        dataHeld = true,
      ),
    )

    backlogRequestService.getByIdOrNull(backlogRequest.id)?.let { actual ->
      assertThat(actual.serviceSummary).hasSize(1)
      assertThat(actual.serviceSummary[0].id).isEqualTo(summary1.id)
      assertThat(actual.serviceSummary[0].backlogRequest?.id).isEqualTo(summary1.backlogRequest?.id)
      assertThat(actual.serviceSummary[0].serviceName).isEqualTo(serviceOneConfiguration.serviceName)
      assertThat(actual.serviceSummary[0].serviceOrder).isEqualTo(serviceOneConfiguration.order)
      assertThat(actual.serviceSummary[0].dataHeld).isTrue()
      assertThat(actual.serviceSummary[0].status).isEqualTo(BacklogRequestStatus.COMPLETE)
    } ?: fail("expected BacklogRequest did not exist")
  }

  @Test
  fun `should add service summary existing entry with serviceName does not already exist`() {
    var backlogRequest = BacklogRequest()
    backlogRequest = backlogRequestService.save(backlogRequest)
    assertThat(backlogRequest.serviceSummary).isEmpty()

    // Update the service summary
    backlogRequestService.addServiceSummary(
      backlogRequest,
      ServiceSummary(
        serviceName = "service1",
        serviceOrder = 1,
        status = BacklogRequestStatus.COMPLETE,
        dataHeld = true,
      ),
    )

    backlogRequestService.getByIdOrNull(backlogRequest.id)?.let { actual ->
      assertThat(actual.serviceSummary).hasSize(1)
      assertThat(actual.serviceSummary[0].id).isNotNull()
      assertThat(actual.serviceSummary[0].backlogRequest?.id).isEqualTo(backlogRequest.id)
      assertThat(actual.serviceSummary[0].serviceName).isEqualTo(serviceOneConfiguration.serviceName)
      assertThat(actual.serviceSummary[0].serviceOrder).isEqualTo(serviceOneConfiguration.order)
      assertThat(actual.serviceSummary[0].dataHeld).isTrue()
      assertThat(actual.serviceSummary[0].status).isEqualTo(BacklogRequestStatus.COMPLETE)
    } ?: fail("expected BacklogRequest did not exist")
  }
}
