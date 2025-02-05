package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.FatalSubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.ServiceConfigurationRepository
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ServiceConfigurationServiceTest {

  private val serviceConfigurationRepository: ServiceConfigurationRepository = mock()
  private val subjectAccessRequest: SubjectAccessRequest = mock()

  private val service = ServiceConfigurationService(serviceConfigurationRepository)
  private val invalidServiceName = "this-service-does-not-exist"

  @Captor
  private lateinit var serviceNameCaptor: ArgumentCaptor<String>

  @Test
  fun `success should return all expected services`() {
    whenever(subjectAccessRequest.services)
      .thenReturn("service1,service2")

    whenever(serviceConfigurationRepository.findByServiceName("service1"))
      .thenReturn(service1Configuration)

    whenever(serviceConfigurationRepository.findByServiceName("service2"))
      .thenReturn(service2Configuration)

    val actual = service.getSelectedServices(subjectAccessRequest)

    assertThat(actual).hasSize(2)
    assertThat(actual[0]).isEqualTo(dpsService1)
    assertThat(actual[1]).isEqualTo(dpsService2)

    verify(serviceConfigurationRepository, times(1)).findByServiceName("service1")
    verify(serviceConfigurationRepository, times(1)).findByServiceName("service2")
  }

  @Test
  fun `should return services in serviceConfiguration_order ascending order`() {
    whenever(subjectAccessRequest.services)
      .thenReturn("service2,service99,service1")

    whenever(serviceConfigurationRepository.findByServiceName("service2"))
      .thenReturn(service2Configuration)

    whenever(serviceConfigurationRepository.findByServiceName("service99"))
      .thenReturn(service99Configuration)

    whenever(serviceConfigurationRepository.findByServiceName("service1"))
      .thenReturn(service1Configuration)

    val actual = service.getSelectedServices(subjectAccessRequest)

    assertThat(actual).hasSize(3)
    assertThat(actual[0]).isEqualTo(dpsService1)
    assertThat(actual[1]).isEqualTo(dpsService2)
    assertThat(actual[2]).isEqualTo(dpsService99)

    verify(serviceConfigurationRepository, times(1)).findByServiceName("service1")
    verify(serviceConfigurationRepository, times(1)).findByServiceName("service2")
    verify(serviceConfigurationRepository, times(1)).findByServiceName("service99")
  }

  @Test
  fun `should throw fatal exception when requested service is not found`() {
    whenever(subjectAccessRequest.services)
      .thenReturn(invalidServiceName)

    whenever(serviceConfigurationRepository.findByServiceName("this-service-does-not-exist"))
      .thenReturn(null)

    val actual = assertThrows<FatalSubjectAccessRequestException> {
      service.getSelectedServices(subjectAccessRequest)
    }

    assertThat(actual.message).contains("service with name '$invalidServiceName' not found")
    assertThat(actual.event).isEqualTo(ProcessingEvent.GET_SERVICE_CONFIGURATION)
    assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)
    assertThat(actual.params).containsExactlyEntriesOf(mutableMapOf("serviceName" to invalidServiceName))
  }

  @ParameterizedTest
  @MethodSource("serviceNameInputs")
  fun `should handle services value correctly`(testCase: ServiceNameTestCase) {
    whenever(subjectAccessRequest.services)
      .thenReturn(testCase.input)

    whenever(serviceConfigurationRepository.findByServiceName(testCase.expectedResult.name ?: ""))
      .thenReturn(testCase.expectedServiceConfiguration)

    val actual = service.getSelectedServices(subjectAccessRequest)
    assertThat(actual).hasSize(1)
    assertThat(actual[0]).isEqualTo(testCase.expectedResult)

    verify(serviceConfigurationRepository, times(1)).findByServiceName(capture(serviceNameCaptor))
    assertThat(serviceNameCaptor.allValues).hasSize(1)
    assertThat(serviceNameCaptor.allValues[0]).isEqualTo(testCase.expectedResult.name)
  }

  data class ServiceNameTestCase(
    val description: String,
    val input: String,
    val expectedServiceConfiguration: ServiceConfiguration? = null,
    val expectedResult: DpsService,
  ) {
    override fun toString(): String {
      return this.description
    }
  }

  companion object {

    private val dpsService1 = DpsService(
      name = "service1",
      businessName = "Service One",
      url = "service-one.com",
      orderPosition = 1,
    )

    private val dpsService2 = DpsService(
      name = "service2",
      businessName = "Service Two",
      url = "service-two.com",
      orderPosition = 2,
    )

    private val dpsService99 = DpsService(
      name = "service99",
      businessName = "Service Ninety Nine",
      url = "service-Ninety-Nine.com",
      orderPosition = 99,
    )

    private val service1Configuration = ServiceConfiguration(
      id = UUID.randomUUID(),
      serviceName = "service1",
      label = "Service One",
      url = "service-one.com",
      order = 1,
      enabled = true,
    )

    private val service2Configuration = ServiceConfiguration(
      id = UUID.randomUUID(),
      serviceName = "service2",
      label = "Service Two",
      url = "service-two.com",
      order = 2,
      enabled = true,
    )

    private val service99Configuration = ServiceConfiguration(
      id = UUID.randomUUID(),
      serviceName = "service99",
      label = "Service Ninety Nine",
      url = "service-Ninety-Nine.com",
      order = 99,
      enabled = true,
    )

    @JvmStatic
    fun serviceNameInputs(): List<ServiceNameTestCase> = listOf(
      ServiceNameTestCase(
        description = "Service name with leading whitespace",
        input = "   service1",
        expectedServiceConfiguration = service1Configuration,
        expectedResult = dpsService1,
      ),
      ServiceNameTestCase(
        description = "Service name with trailing whitespace",
        input = "service1   ",
        expectedServiceConfiguration = service1Configuration,
        expectedResult = dpsService1,
      ),
      ServiceNameTestCase(
        description = "Service name with leading and trailing whitespace",
        input = "    service1   ",
        expectedServiceConfiguration = service1Configuration,
        expectedResult = dpsService1,
      ),
      ServiceNameTestCase(
        description = "Service name with trailing whitespace and comma",
        input = "service1   ,",
        expectedServiceConfiguration = service1Configuration,
        expectedResult = dpsService1,
      ),
      ServiceNameTestCase(
        description = "Service name with trailing comma",
        input = "service1   ,",
        expectedServiceConfiguration = service1Configuration,
        expectedResult = dpsService1,
      ),
      ServiceNameTestCase(
        description = "Service name with comma and trailing whitespace",
        input = "service1,   ",
        expectedServiceConfiguration = service1Configuration,
        expectedResult = dpsService1,
      ),
    )
  }
}
