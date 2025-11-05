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

  private val service = ServiceConfigurationService(
    serviceConfigurationRepository = serviceConfigurationRepository,
    g1ApiUrl = G1_API_URL,
    g2ApiUrl = G2_API_URL,
    g3ApiUrl = G3_API_URL,
  )

  @Captor
  private lateinit var serviceNameCaptor: ArgumentCaptor<String>

  companion object {
    private const val G1_API_URL = "http://www.magical-law-enforcement.ministry-of-magic.owl"
    private const val G2_API_URL = "http://www.magical-accidents-and-catastrophes.ministry-of-magic.owl"
    private const val G3_API_URL = "http://www.international-magical-cooperation.ministry-of-magic.owl"

    private val service1Configuration = serviceConfiguration(order = 1)
    private val service2Configuration = serviceConfiguration(order = 2)
    private val serviceG1Configuration = serviceConfiguration(serviceName = "G1", label = "G1", url = "G1", order = 3)
    private val serviceG2Configuration = serviceConfiguration(serviceName = "G2", label = "G2", url = "G2", order = 4)
    private val serviceG3Configuration = serviceConfiguration(serviceName = "G3", label = "G3", url = "G3", order = 5)
    private val service99Configuration = serviceConfiguration(order = 99)

    private fun serviceConfiguration(
      serviceName: String? = null,
      label: String? = null,
      url: String? = null,
      order: Int,
    ) = ServiceConfiguration(
      id = UUID.randomUUID(),
      serviceName = serviceName ?: "service$order",
      label = label ?: "Service $order",
      url = url ?: "service-$order.com",
      order = order,
      enabled = true,
    )

    private fun dpsService(
      name: String? = null,
      businessName: String? = null,
      url: String? = null,
      order: Int,
    ) = DpsService(
      name = name ?: "service$order",
      businessName = businessName ?: "Service $order",
      url = url ?: "service-$order.com",
      orderPosition = order,
    )

    @JvmStatic
    fun serviceNameInputs(): List<ServiceNameTestCase> = listOf(
      ServiceNameTestCase(
        description = "Service name with leading whitespace",
        input = "   service1",
        expectedServiceConfiguration = service1Configuration,
      ),
      ServiceNameTestCase(
        description = "Service name with trailing whitespace",
        input = "service1   ",
        expectedServiceConfiguration = service1Configuration,
      ),
      ServiceNameTestCase(
        description = "Service name with leading and trailing whitespace",
        input = "    service1   ",
        expectedServiceConfiguration = service1Configuration,
      ),
      ServiceNameTestCase(
        description = "Service name with trailing whitespace and comma",
        input = "service1   ,",
        expectedServiceConfiguration = service1Configuration,
      ),
      ServiceNameTestCase(
        description = "Service name with trailing comma",
        input = "service1,",
        expectedServiceConfiguration = service1Configuration,
      ),
      ServiceNameTestCase(
        description = "Service name with comma and trailing whitespace",
        input = "service1,   ",
        expectedServiceConfiguration = service1Configuration,
      ),
    )

    @JvmStatic
    fun sensitiveServiceConfigurationsTestCases() = listOf(
      SensitiveServiceTestCase("G1", serviceG1Configuration, "G1", G1_API_URL),
      SensitiveServiceTestCase("G2", serviceG2Configuration, "G2", G2_API_URL),
      SensitiveServiceTestCase("G3", serviceG3Configuration, "G3", G3_API_URL),
    )
  }

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
    assertThat(actual[0]).isEqualTo(service1Configuration)
    assertThat(actual[1]).isEqualTo(service2Configuration)

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
    assertThat(actual[0]).isEqualTo(service1Configuration)
    assertThat(actual[1]).isEqualTo(service2Configuration)
    assertThat(actual[2]).isEqualTo(service99Configuration)

    verify(serviceConfigurationRepository, times(1)).findByServiceName("service1")
    verify(serviceConfigurationRepository, times(1)).findByServiceName("service2")
    verify(serviceConfigurationRepository, times(1)).findByServiceName("service99")
  }

  @Test
  fun `should throw fatal exception when requested service is not found`() {
    whenever(subjectAccessRequest.services)
      .thenReturn("this-service-does-not-exist")

    whenever(serviceConfigurationRepository.findByServiceName("this-service-does-not-exist"))
      .thenReturn(null)

    val actual = assertThrows<FatalSubjectAccessRequestException> {
      service.getSelectedServices(subjectAccessRequest)
    }

    assertThat(actual.message).contains("service with name 'this-service-does-not-exist' not found")
    assertThat(actual.event).isEqualTo(ProcessingEvent.GET_SERVICE_CONFIGURATION)
    assertThat(actual.subjectAccessRequest).isEqualTo(subjectAccessRequest)
    assertThat(actual.params).containsExactlyEntriesOf(mutableMapOf("serviceName" to "this-service-does-not-exist"))
  }

  @ParameterizedTest
  @MethodSource("serviceNameInputs")
  fun `should handle services name value correctly`(testCase: ServiceNameTestCase) {
    whenever(subjectAccessRequest.services)
      .thenReturn(testCase.input)

    whenever(serviceConfigurationRepository.findByServiceName(testCase.expectedServiceConfiguration!!.serviceName))
      .thenReturn(testCase.expectedServiceConfiguration)

    val actual = service.getSelectedServices(subjectAccessRequest)
    assertThat(actual).hasSize(1)
    assertThat(actual[0]).isEqualTo(testCase.expectedServiceConfiguration)

    verify(serviceConfigurationRepository, times(1)).findByServiceName(capture(serviceNameCaptor))
    assertThat(serviceNameCaptor.allValues).hasSize(1)
    assertThat(serviceNameCaptor.allValues[0]).isEqualTo(testCase.expectedServiceConfiguration.serviceName)
  }

  @ParameterizedTest
  @MethodSource("sensitiveServiceConfigurationsTestCases")
  fun `should resolve sensitive service url placeholders`(testCase: SensitiveServiceTestCase) {
    whenever(subjectAccessRequest.services)
      .thenReturn(testCase.serviceNamePlaceholder)

    whenever(serviceConfigurationRepository.findByServiceName(testCase.serviceNamePlaceholder))
      .thenReturn(testCase.serviceConfiguration)

    val actual = service.getSelectedServices(subjectAccessRequest)
    assertThat(actual).hasSize(1)
    assertThat(actual[0]).isEqualTo(testCase.serviceConfiguration)

    verify(serviceConfigurationRepository, times(1)).findByServiceName(capture(serviceNameCaptor))
    assertThat(serviceNameCaptor.allValues).hasSize(1)
    assertThat(serviceNameCaptor.allValues[0]).isEqualTo(testCase.expectedServiceName)
  }

  data class ServiceNameTestCase(
    val description: String,
    val input: String,
    val expectedServiceConfiguration: ServiceConfiguration? = null,
  ) {
    override fun toString(): String = this.description
  }

  data class SensitiveServiceTestCase(
    val serviceNamePlaceholder: String,
    val serviceConfiguration: ServiceConfiguration,
    val expectedServiceName: String,
    val expectedServiceUrl: String,
  )
}
