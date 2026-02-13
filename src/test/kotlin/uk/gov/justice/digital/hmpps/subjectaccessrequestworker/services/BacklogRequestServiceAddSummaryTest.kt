package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.HtmlRendererApiClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory
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
  private val htmlRendererApiClient: HtmlRendererApiClient = mock()
  private val serviceConfigMock: ServiceConfiguration = mock()

  @Captor
  private lateinit var saveServiceSummaryCaptor: ArgumentCaptor<ServiceSummary>

  private val backlogRequestService = BacklogRequestService(
    backlogRequestRepository,
    serviceSummaryRepository,
    serviceConfigurationService,
    htmlRendererApiClient,
    5,
  )

  private val existingBacklogRequest = BacklogRequest()

  private val serviceOneConfig = ServiceConfiguration(
    serviceName = "service1",
    label = "Service One",
    url = "http://localhost:1234",
    enabled = true,
    category = ServiceCategory.PRISON,
  )

  private val disabledServiceConfig = ServiceConfiguration(
    serviceName = "service2",
    label = "Service Two",
    url = "http://localhost:1234",
    enabled = false,
    category = ServiceCategory.PROBATION,
  )

  private val existingServiceSummary = ServiceSummary(
    serviceConfiguration = serviceOneConfig,
    status = BacklogRequestStatus.PENDING,
    dataHeld = false,
    backlogRequest = existingBacklogRequest,
  )

  @BeforeEach
  fun setup() {
    whenever(serviceConfigMock.serviceName).thenReturn("service-1")
    whenever(serviceConfigMock.id).thenReturn(UUID.randomUUID())
  }

  @Test
  fun `should throw exception if serviceConfiguration is null`() {
    val actual = assertThrows<BacklogRequestException> {
      backlogRequestService.addServiceSummary(
        existingBacklogRequest,
        ServiceSummary(serviceConfiguration = null),
      )
    }

    assertThat(actual.message).isEqualTo("Service configuration cannot be empty")
    assertThat(actual.backlogRequestId).isEqualTo(existingBacklogRequest.id)
  }

  @Test
  fun `should throw exception if service configuration serviceName is empty`() {
    whenever(serviceConfigMock.serviceName).thenReturn(null)

    val actual = assertThrows<BacklogRequestException> {
      backlogRequestService.addServiceSummary(
        existingBacklogRequest,
        ServiceSummary(serviceConfiguration = serviceConfigMock),
      )
    }

    assertThat(actual.message).isEqualTo("Service configuration name cannot be empty")
    assertThat(actual.backlogRequestId).isEqualTo(existingBacklogRequest.id)
  }

  @Test
  fun `should throw exception if service configuration id is null`() {
    whenever(serviceConfigMock.id).thenReturn(null)

    val actual = assertThrows<BacklogRequestException> {
      backlogRequestService.addServiceSummary(
        existingBacklogRequest,
        ServiceSummary(serviceConfiguration = serviceConfigMock),
      )
    }

    assertThat(actual.message).isEqualTo("Service configuration id cannot be empty")
    assertThat(actual.backlogRequestId).isEqualTo(existingBacklogRequest.id)
  }

  @Test
  fun `should throw exception if serviceName does not exist in service configuration`() {
    val actual = assertThrows<BacklogRequestException> {
      backlogRequestService.addServiceSummary(
        existingBacklogRequest,
        ServiceSummary(serviceConfiguration = serviceConfigMock),
      )
    }

    assertThat(actual.message).isEqualTo("Service Configuration does not exist for serviceName")
    assertThat(actual.backlogRequestId).isEqualTo(existingBacklogRequest.id)
  }

  @Test
  fun `should update existing service summary if already exists`() {
    whenever(
      serviceSummaryRepository.findOneByBacklogRequestIdAndServiceConfigurationId(
        existingBacklogRequest.id,
        serviceOneConfig.id,
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
        serviceConfiguration = serviceOneConfig,
        status = BacklogRequestStatus.COMPLETE,
        dataHeld = true,
      ),
    )

    assertThat(saveServiceSummaryCaptor.allValues).hasSize(1)
    val actual = saveServiceSummaryCaptor.allValues[0]

    assertThat(actual.id).isEqualTo(existingServiceSummary.id)
    assertThat(actual.backlogRequest?.id).isEqualTo(existingServiceSummary.backlogRequest?.id)
    assertThat(actual.serviceConfiguration).isEqualTo(serviceOneConfig)
    assertThat(actual.dataHeld).isTrue()
    assertThat(actual.status).isEqualTo(BacklogRequestStatus.COMPLETE)

    verifyNoInteractions(backlogRequestRepository)
  }

  @Test
  fun `should retain exiting service summary ID when updating an existing entry`() {
    whenever(
      serviceSummaryRepository.findOneByBacklogRequestIdAndServiceConfigurationId(
        existingBacklogRequest.id,
        serviceOneConfig.id,
      ),
    ).thenReturn(existingServiceSummary)

    whenever(serviceConfigurationService.findByServiceName(serviceOneConfig.serviceName))
      .thenReturn(serviceOneConfig)

    whenever(serviceSummaryRepository.saveAndFlush(capture(saveServiceSummaryCaptor)))
      .thenAnswer {
        ServiceSummary(
          serviceConfiguration = serviceOneConfig,
          status = BacklogRequestStatus.COMPLETE,
          dataHeld = true,
        )
      }

    // Update the service summary
    backlogRequestService.addServiceSummary(
      existingBacklogRequest,
      ServiceSummary(
        id = UUID.randomUUID(), // Intentionally set the ID to a different value/.
        serviceConfiguration = serviceOneConfig,
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
      serviceSummaryRepository.findOneByBacklogRequestIdAndServiceConfigurationId(
        existingBacklogRequest.id,
        serviceOneConfig.id,
      ),
    ).thenReturn(null)

    whenever(serviceSummaryRepository.saveAndFlush(capture(saveServiceSummaryCaptor))).thenAnswer { args ->
      args.getArgument<ServiceSummary>(0)
    }

    val summary = ServiceSummary(
      serviceConfiguration = serviceOneConfig,
      status = BacklogRequestStatus.COMPLETE,
      dataHeld = true,
    )

    // Update the service summary
    backlogRequestService.addServiceSummary(existingBacklogRequest, summary)

    assertThat(saveServiceSummaryCaptor.allValues).hasSize(1)
    val actual = saveServiceSummaryCaptor.allValues[0]

    assertThat(actual.id).isEqualTo(summary.id)
    assertThat(actual.backlogRequest?.id).isEqualTo(existingBacklogRequest.id)
    assertThat(actual.serviceConfiguration).isEqualTo(serviceOneConfig)
    assertThat(actual.status).isEqualTo(BacklogRequestStatus.COMPLETE)
    assertThat(actual.dataHeld).isTrue()

    verify(serviceSummaryRepository, times(1)).findOneByBacklogRequestIdAndServiceConfigurationId(
      existingBacklogRequest.id,
      serviceOneConfig.id,
    )
    verify(serviceSummaryRepository, times(1)).saveAndFlush(any())
  }

  @Test
  fun `should not add service summary if the service is disabled`() {
    whenever(serviceConfigurationService.findByServiceName(disabledServiceConfig.serviceName))
      .thenReturn(disabledServiceConfig)

    backlogRequestService.addServiceSummary(
      existingBacklogRequest,
      ServiceSummary(serviceConfiguration = disabledServiceConfig),
    )

    verifyNoInteractions(serviceSummaryRepository)
    verify(serviceConfigurationService, times(1)).findByServiceName(disabledServiceConfig.serviceName)
  }
}
