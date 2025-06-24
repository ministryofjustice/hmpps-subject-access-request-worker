package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.BacklogRequestRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceSummaryRepository
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class BacklogRequestServiceAddSummaryTest {
  private val backlogRequestRepository: BacklogRequestRepository = mock()
  private val serviceSummaryRepository: ServiceSummaryRepository = mock()
  private val serviceConfigurationService: ServiceConfigurationService = mock()
  private val dynamicServicesClient: DynamicServicesClient = mock()

  @Captor
  private lateinit var saveServiceSummaryCaptor: ArgumentCaptor<ServiceSummary>

  private val backlogRequestService = BacklogRequestService(
    backlogRequestRepository,
    serviceSummaryRepository,
    serviceConfigurationService,
    dynamicServicesClient,
    5,
  )

  private val existingBacklogRequest = BacklogRequest()

  private val existingServiceSummary = ServiceSummary(
    serviceName = "service1",
    status = BacklogRequestStatus.PENDING,
    dataHeld = false,
    backlogRequest = existingBacklogRequest,
  )

  private val serviceOneConfig = ServiceConfiguration(
    serviceName = "service1",
    label = "Service One",
    url = "http://localhost:1234",
    order = 1,
    enabled = true,
  )

  @Test
  fun `should throw exception if serviceName is empty`() {
    val actual = assertThrows<BacklogRequestException> {
      backlogRequestService.addServiceSummary(existingBacklogRequest, ServiceSummary())
    }

    assertThat(actual.message).isEqualTo("Service name cannot be empty")
    assertThat(actual.backlogRequestId).isEqualTo(existingBacklogRequest.id)
  }

  @Test
  fun `should throw exception if serviceName does not exist in service configuration`() {
    val actual = assertThrows<BacklogRequestException> {
      backlogRequestService.addServiceSummary(existingBacklogRequest, ServiceSummary(serviceName = "madeUpService"))
    }

    assertThat(actual.message).isEqualTo("Service Configuration does not exist for serviceName")
    assertThat(actual.backlogRequestId).isEqualTo(existingBacklogRequest.id)
  }

  @Test
  fun `should update existing service summary if already exists`() {
    whenever(
      serviceSummaryRepository.findOneByBacklogRequestIdAndServiceName(
        existingBacklogRequest.id,
        serviceOneConfig.serviceName,
      ),
    ).thenReturn(existingServiceSummary)

    whenever(serviceConfigurationService.findByServiceName(serviceOneConfig.serviceName))
      .thenReturn(serviceOneConfig)

    whenever(serviceSummaryRepository.saveAndFlush(capture(saveServiceSummaryCaptor)))
      .thenAnswer {
        // Return only required to satisfy Mockito value not used.
        existingServiceSummary
      }

    // Update the service summary
    backlogRequestService.addServiceSummary(
      existingBacklogRequest,
      ServiceSummary(
        serviceName = serviceOneConfig.serviceName,
        serviceOrder = serviceOneConfig.order,
        status = BacklogRequestStatus.COMPLETE,
        dataHeld = true,
      ),
    )

    assertThat(saveServiceSummaryCaptor.allValues).hasSize(1)
    val actual = saveServiceSummaryCaptor.allValues[0]

    assertThat(actual.id).isEqualTo(existingServiceSummary.id)
    assertThat(actual.backlogRequest?.id).isEqualTo(existingServiceSummary.backlogRequest?.id)
    assertThat(actual.serviceName).isEqualTo(serviceOneConfig.serviceName)
    assertThat(actual.serviceOrder).isEqualTo(serviceOneConfig.order)
    assertThat(actual.dataHeld).isTrue()
    assertThat(actual.status).isEqualTo(BacklogRequestStatus.COMPLETE)

    verifyNoInteractions(backlogRequestRepository)
  }

  @Test
  fun `should retain exiting service summary ID when updating an existing entry`() {
    whenever(
      serviceSummaryRepository.findOneByBacklogRequestIdAndServiceName(
        existingBacklogRequest.id,
        serviceOneConfig.serviceName,
      ),
    ).thenReturn(existingServiceSummary)

    whenever(serviceConfigurationService.findByServiceName(serviceOneConfig.serviceName))
      .thenReturn(serviceOneConfig)

    whenever(serviceSummaryRepository.saveAndFlush(capture(saveServiceSummaryCaptor)))
      .thenAnswer {
        ServiceSummary(
          serviceName = serviceOneConfig.serviceName,
          serviceOrder = serviceOneConfig.order,
          status = BacklogRequestStatus.COMPLETE,
          dataHeld = true,
        )
      }

    // Update the service summary
    backlogRequestService.addServiceSummary(
      existingBacklogRequest,
      ServiceSummary(
        id = UUID.randomUUID(), // Intentionally set the ID to a different value/.
        serviceName = serviceOneConfig.serviceName,
        serviceOrder = serviceOneConfig.order,
        status = BacklogRequestStatus.COMPLETE,
        dataHeld = true,
      ),
    )

    assertThat(saveServiceSummaryCaptor.allValues).hasSize(1)
    assertThat(saveServiceSummaryCaptor.allValues[0].id).isEqualTo(existingServiceSummary.id)
  }

  @Test
  fun `should add service summary existing entry with serviceName does not already exist`() {
    whenever(serviceConfigurationService.findByServiceName(serviceOneConfig.serviceName))
      .thenReturn(serviceOneConfig)

    whenever(
      serviceSummaryRepository.findOneByBacklogRequestIdAndServiceName(
        existingBacklogRequest.id,
        serviceOneConfig.serviceName,
      ),
    ).thenReturn(null)

    whenever(serviceSummaryRepository.saveAndFlush(capture(saveServiceSummaryCaptor))).thenAnswer { args ->
      args.getArgument<ServiceSummary>(0)
    }

    val summary = ServiceSummary(
      serviceName = serviceOneConfig.serviceName,
      serviceOrder = serviceOneConfig.order,
      status = BacklogRequestStatus.COMPLETE,
      dataHeld = true,
    )

    // Update the service summary
    backlogRequestService.addServiceSummary(existingBacklogRequest, summary)

    assertThat(saveServiceSummaryCaptor.allValues).hasSize(1)
    val actual = saveServiceSummaryCaptor.allValues[0]

    assertThat(actual.id).isEqualTo(summary.id)
    assertThat(actual.backlogRequest?.id).isEqualTo(existingBacklogRequest.id)
    assertThat(actual.serviceName).isEqualTo("service1")
    assertThat(actual.serviceOrder).isEqualTo(1)
    assertThat(actual.status).isEqualTo(BacklogRequestStatus.COMPLETE)
    assertThat(actual.dataHeld).isTrue()

    verify(serviceSummaryRepository, times(1)).findOneByBacklogRequestIdAndServiceName(
      existingBacklogRequest.id,
      serviceOneConfig.serviceName,
    )
    verify(serviceSummaryRepository, times(1)).saveAndFlush(any())
  }
}
