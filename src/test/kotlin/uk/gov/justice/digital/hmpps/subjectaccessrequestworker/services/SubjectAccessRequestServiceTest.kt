package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.events.ProcessingEvent.UPDATE_SAR_SERVICE_RENDER_STATUS
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.errorcode.ErrorCode.Companion.INTERNAL_SERVER_ERROR
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RenderStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.RequestServiceDetail
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.TemplateVersion
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.SubjectAccessRequestRepository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository.TemplateVersionRepository
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SubjectAccessRequestServiceTest {

  private val subjectAccessRequestRepository: SubjectAccessRequestRepository = mock()
  private val templateVersionRepository: TemplateVersionRepository = mock()

  private val service = SubjectAccessRequestService(subjectAccessRequestRepository, templateVersionRepository, 30)

  @Captor
  private val sarCaptor: ArgumentCaptor<SubjectAccessRequest> = ArgumentCaptor.forClass(SubjectAccessRequest::class.java)

  private val serviceConfig = ServiceConfiguration(
    serviceName = "my-service",
    label = "My service",
    url = "http://url",
    enabled = true,
    templateMigrated = true,
    category = ServiceCategory.PRISON,
  )
  private val serviceConfigNotMigrated = ServiceConfiguration(
    serviceName = "my-service-two",
    label = "My service two",
    url = "http://url-two",
    enabled = true,
    templateMigrated = false,
    category = ServiceCategory.PRISON,
  )
  private val sar = SubjectAccessRequest(id = UUID.randomUUID()).also {
    it.services.addAll(
      listOf(
        RequestServiceDetail(
          subjectAccessRequest = it,
          serviceConfiguration = serviceConfig,
          renderStatus = RenderStatus.PENDING,
        ),
        RequestServiceDetail(
          subjectAccessRequest = it,
          serviceConfiguration = serviceConfigNotMigrated,
          renderStatus = RenderStatus.PENDING,
        ),
      ),
    )
  }
  private val templateVersion = TemplateVersion(version = 1)

  @AfterEach
  fun afterEach() {
    verifyNoMoreInteractions(subjectAccessRequestRepository, templateVersionRepository)
  }

  @Nested
  inner class UpdateServiceStatusSuccess {

    @Test
    fun `should throw exception when update as success and request does not exist`() {
      whenever(subjectAccessRequestRepository.findById(sar.id)).thenReturn(Optional.empty())

      val exception = assertThrows<SubjectAccessRequestException> { service.updateServiceStatusSuccess(sar.id, serviceConfig.serviceName, "1") }

      verify(subjectAccessRequestRepository).findById(sar.id)
      assertThat(exception)
        .hasMessageStartingWith("Subject access request with id ${sar.id} not found")
        .returns(UPDATE_SAR_SERVICE_RENDER_STATUS, SubjectAccessRequestException::event)
        .returns(INTERNAL_SERVER_ERROR, SubjectAccessRequestException::errorCode)
    }

    @Test
    fun `should throw exception when update as success and service does not exist`() {
      whenever(subjectAccessRequestRepository.findById(sar.id)).thenReturn(Optional.of(sar))

      val exception = assertThrows<SubjectAccessRequestException> { service.updateServiceStatusSuccess(sar.id, "other-name", "1") }

      verify(subjectAccessRequestRepository).findById(sar.id)
      assertThat(exception)
        .hasMessageStartingWith("Subject access request service not found")
        .returns(UPDATE_SAR_SERVICE_RENDER_STATUS, SubjectAccessRequestException::event)
        .returns(INTERNAL_SERVER_ERROR, SubjectAccessRequestException::errorCode)
        .returns(sar, SubjectAccessRequestException::subjectAccessRequest)
        .returns(mapOf("service" to "other-name"), SubjectAccessRequestException::params)
    }

    @Test
    fun `should throw exception when update as success and template version does not exist`() {
      whenever(subjectAccessRequestRepository.findById(sar.id)).thenReturn(Optional.of(sar))

      val exception = assertThrows<SubjectAccessRequestException> { service.updateServiceStatusSuccess(sar.id, serviceConfig.serviceName, "2") }

      verify(subjectAccessRequestRepository).findById(sar.id)
      verify(templateVersionRepository).findByServiceConfigurationIdAndVersion(serviceConfig.id, 2)
      assertThat(exception)
        .hasMessageStartingWith("Could not find template version for service")
        .returns(UPDATE_SAR_SERVICE_RENDER_STATUS, SubjectAccessRequestException::event)
        .returns(INTERNAL_SERVER_ERROR, SubjectAccessRequestException::errorCode)
        .returns(sar, SubjectAccessRequestException::subjectAccessRequest)
        .returns(mapOf("version" to "2", "service" to "my-service"), SubjectAccessRequestException::params)
    }

    @Test
    fun `should update service status and template version`() {
      whenever(subjectAccessRequestRepository.findById(sar.id)).thenReturn(Optional.of(sar))
      whenever(templateVersionRepository.findByServiceConfigurationIdAndVersion(serviceConfig.id, 1)).thenReturn(
        templateVersion,
      )

      service.updateServiceStatusSuccess(sar.id, serviceConfig.serviceName, "1")

      verify(subjectAccessRequestRepository).findById(sar.id)
      verify(templateVersionRepository).findByServiceConfigurationIdAndVersion(serviceConfig.id, 1)
      verify(subjectAccessRequestRepository).save(sarCaptor.capture())
      assertThat(sarCaptor.firstValue.services[0])
        .returns(RenderStatus.COMPLETE, RequestServiceDetail::renderStatus)
        .returns(templateVersion, RequestServiceDetail::templateVersion)
    }

    @Test
    fun `should update service status only when not migrated`() {
      whenever(subjectAccessRequestRepository.findById(sar.id)).thenReturn(Optional.of(sar))

      service.updateServiceStatusSuccess(sar.id, serviceConfigNotMigrated.serviceName, "1")

      verify(subjectAccessRequestRepository).findById(sar.id)
      verify(subjectAccessRequestRepository).save(sarCaptor.capture())
      assertThat(sarCaptor.firstValue.services[1])
        .returns(RenderStatus.COMPLETE, RequestServiceDetail::renderStatus)
        .returns(null, RequestServiceDetail::templateVersion)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "legacy"])
    fun `should update service status only when non numeric version`(version: String?) {
      whenever(subjectAccessRequestRepository.findById(sar.id)).thenReturn(Optional.of(sar))

      service.updateServiceStatusSuccess(sar.id, serviceConfig.serviceName, version)

      verify(subjectAccessRequestRepository).findById(sar.id)
      verify(subjectAccessRequestRepository).save(sarCaptor.capture())
      assertThat(sarCaptor.firstValue.services[0])
        .returns(RenderStatus.COMPLETE, RequestServiceDetail::renderStatus)
        .returns(null, RequestServiceDetail::templateVersion)
    }
  }

  @Nested
  inner class UpdateServiceStatusFailure {

    @Test
    fun `should throw exception when update as failed and request does not exist`() {
      whenever(subjectAccessRequestRepository.findById(sar.id)).thenReturn(Optional.empty())

      val exception = assertThrows<SubjectAccessRequestException> { service.updateServiceStatusFailed(sar.id, serviceConfig.serviceName) }

      verify(subjectAccessRequestRepository).findById(sar.id)
      assertThat(exception)
        .hasMessageStartingWith("Subject access request with id ${sar.id} not found")
        .returns(UPDATE_SAR_SERVICE_RENDER_STATUS, SubjectAccessRequestException::event)
        .returns(INTERNAL_SERVER_ERROR, SubjectAccessRequestException::errorCode)
    }

    @Test
    fun `should throw exception when update as failed and service does not exist`() {
      whenever(subjectAccessRequestRepository.findById(sar.id)).thenReturn(Optional.of(sar))

      val exception = assertThrows<SubjectAccessRequestException> { service.updateServiceStatusFailed(sar.id, "other-name") }

      verify(subjectAccessRequestRepository).findById(sar.id)
      assertThat(exception)
        .hasMessageStartingWith("Subject access request service not found")
        .returns(UPDATE_SAR_SERVICE_RENDER_STATUS, SubjectAccessRequestException::event)
        .returns(INTERNAL_SERVER_ERROR, SubjectAccessRequestException::errorCode)
        .returns(sar, SubjectAccessRequestException::subjectAccessRequest)
        .returns(mapOf("service" to "other-name"), SubjectAccessRequestException::params)
    }

    @Test
    fun `should update service status only when failed`() {
      whenever(subjectAccessRequestRepository.findById(sar.id)).thenReturn(Optional.of(sar))
      whenever(templateVersionRepository.findByServiceConfigurationIdAndVersion(serviceConfig.id, 1)).thenReturn(
        templateVersion,
      )

      service.updateServiceStatusFailed(sar.id, serviceConfig.serviceName)

      verify(subjectAccessRequestRepository).findById(sar.id)
      verify(subjectAccessRequestRepository).save(sarCaptor.capture())
      assertThat(sarCaptor.firstValue.services[0])
        .returns(RenderStatus.ERRORED, RequestServiceDetail::renderStatus)
        .returns(null, RequestServiceDetail::templateVersion)
    }
  }
}
