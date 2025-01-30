package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.client.DynamicServicesClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.exception.SubjectAccessRequestException
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.mockservers.ServiceOneApiExtension.Companion.serviceOneMockApi
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class GetSubjectAccessRequestDataServiceTest {

  @Captor
  private lateinit var appInsightsEventNameCaptor: ArgumentCaptor<String>

  @Captor
  private lateinit var appInsightsPropertiesCaptor: ArgumentCaptor<MutableMap<String, String>>

  @Captor
  private lateinit var appInsightsMetricsCaptor: ArgumentCaptor<MutableMap<String, Double>>

  private val dynamicServicesClient: DynamicServicesClient = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val getSubjectAccessRequestDataService =
    GetSubjectAccessRequestDataService(dynamicServicesClient, telemetryClient)
  private val dateToFormatted = LocalDate.parse("30/01/2024", DateTimeFormatter.ofPattern("dd/MM/yyyy"))

  private val subjectAccessRequest = SubjectAccessRequest(
    id = UUID.randomUUID(),
    sarCaseReferenceNumber = UUID.randomUUID().toString(),
  )

  @BeforeEach
  fun setUp() {
    MockitoAnnotations.openMocks(this)
    serviceOneMockApi.start()
  }

  @AfterEach
  fun tearDown() {
    serviceOneMockApi.stop()
  }

  @Test
  fun `getSubjectAccessRequestData calls getSubjectAccessRequestDataFromServices with given arguments, including service URL`() {
    whenever(
      dynamicServicesClient.getDataFromService(
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
      ),
    ).thenReturn(
      ResponseEntity.ok().body(
        mapOf(
          "content" to mapOf(
            "prisonerNumber" to "A1234AA",
          ),
        ),
      ),
    )

    val response = getSubjectAccessRequestDataService.requestDataFromServices(
      services = selectedDpsServicesSingle,
      nomisId = "A1234AA",
      dateTo = dateToFormatted,
    )

    assertThat(response[0].content).isEqualTo(mapOf("prisonerNumber" to "A1234AA"))

    verify(dynamicServicesClient, times(1)).getDataFromService(
      "https://test-service.hmpps.service.justice.gov.uk",
      "A1234AA",
      null,
      null,
      LocalDate.of(2024, 1, 30),
      null,
    )
  }

  @Test
  fun `getSubjectAccessRequestData uses the service name as a content key if the business name is not present`() {
    whenever(
      dynamicServicesClient.getDataFromService(
        serviceUrl = anyOrNull(),
        prn = anyOrNull(),
        crn = anyOrNull(),
        dateFrom = anyOrNull(),
        dateTo = anyOrNull(),
        subjectAccessRequest = anyOrNull(),
      ),
    ).thenReturn(
      ResponseEntity.ok().body(
        mapOf(
          "content" to mapOf<String, Any>("prisoner-test-property-business-name" to emptyMap<String, Any>()),
        ),
      ),
    )

    val selectedDpsServices = listOf(
      DpsService(
        name = "hmpps-test-service",
        businessName = null,
        orderPosition = 1,
        url = "https://test-service.hmpps.service.justice.gov.uk",
      ),
      DpsService(
        name = "hmpps-test-service-2",
        businessName = null,
        orderPosition = 1,
        url = "https://test-service-2.hmpps.service.justice.gov.uk",
      ),
    )

    val response = getSubjectAccessRequestDataService.requestDataFromServices(
      services = selectedDpsServices,
      nomisId = "A1234AA",
      dateTo = dateToFormatted,
    )

    assertThat(response[0].content.toString()).isEqualTo("{prisoner-test-property-business-name={}}")
    verify(dynamicServicesClient, times(1)).getDataFromService(serviceUrl = "https://test-service.hmpps.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
    verify(dynamicServicesClient, times(1)).getDataFromService(serviceUrl = "https://test-service-2.hmpps.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
  }

  @Test
  fun `getSubjectAccessRequestData  returns upstream API response data in the correct order`() {
    val selectedDpsServices = mutableListOf(
      DpsService(
        name = "hmpps-service",
        businessName = null,
        orderPosition = 2,
        url = "https://srevice.hmpps.service.justice.gov.uk",
      ),
      DpsService(
        name = "hmpps-service-2",
        businessName = null,
        orderPosition = 3,
        url = "https://service-2.hmpps.service.justice.gov.uk",
      ),
      DpsService(
        name = "hmpps-service-test",
        businessName = null,
        orderPosition = 1,
        url = "https://service-test.hmpps.service.justice.gov.uk",
      ),
    )

    val orderedDpsServices = getSubjectAccessRequestDataService.order(selectedDpsServices)

    assertThat(orderedDpsServices[0].orderPosition).isEqualTo(1)
    assertThat(orderedDpsServices[1].orderPosition).isEqualTo(2)
    assertThat(orderedDpsServices[2].orderPosition).isEqualTo(3)
  }

  @Test
  fun `getSubjectAccessRequestData puts services with no order position last`() {
    val selectedDpsServices = mutableListOf(
      DpsService(
        name = "service-B",
        businessName = null,
        orderPosition = null,
        url = "https://service-b.prison.service.justice.gov.uk",
      ),
      DpsService(
        name = "service-A",
        businessName = null,
        orderPosition = 2,
        url = "https://service-a.prison.service.justice.gov.uk",
      ),
      DpsService(
        name = "service-C",
        businessName = null,
        orderPosition = 1,
        url = "https://service-c.prison.service.justice.gov.uk",
      ),
    )

    val orderedDpsServices = getSubjectAccessRequestDataService.order(selectedDpsServices)

    assertThat(orderedDpsServices[0].orderPosition).isEqualTo(1)
    assertThat(orderedDpsServices[1].orderPosition).isEqualTo(2)
    assertThat(orderedDpsServices[2].orderPosition).isEqualTo(null)
  }

  @Test
  fun `getSubjectAccessRequestData sorts services with no order position alphabetically by name`() {
    val selectedDpsServices = mutableListOf(
      DpsService(
        name = "service-B",
        businessName = null,
        orderPosition = null,
        url = "https://service-b.prison.service.justice.gov.uk",
      ),
      DpsService(
        name = "service-A",
        businessName = null,
        orderPosition = null,
        url = "https://service-a.prison.service.justice.gov.uk",
      ),
      DpsService(
        name = "service-C",
        businessName = null,
        orderPosition = null,
        url = "https://service-c.prison.service.justice.gov.uk",
      ),
    )

    val orderedDpsServices = getSubjectAccessRequestDataService.order(selectedDpsServices)

    assertThat(orderedDpsServices[0].name).isEqualTo("service-A")
    assertThat(orderedDpsServices[1].name).isEqualTo("service-B")
    assertThat(orderedDpsServices[2].name).isEqualTo("service-C")
  }

  @Nested
  inner class GetSubjectAccessRequestDataTelemetryTest

  @Test
  fun `get subject access request data success`() {
    whenever(
      dynamicServicesClient.getDataFromService(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
    ).thenReturn(
      ResponseEntity.ok().body(
        mapOf(
          "content" to mapOf(
            "prisonerNumber" to "A1234AA",
          ),
        ),
      ),
    )

    getSubjectAccessRequestDataService.requestDataFromServices(
      services = selectedDpsServicesSingle,
      nomisId = "A1234AA",
      dateTo = dateToFormatted,
      subjectAccessRequest = subjectAccessRequest,
    )

    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent("https://test-service.hmpps.service.justice.gov.uk")
    assertAppInsightsRequestCompleteEvent()
  }

  @Test
  fun `get subject access request data throws error`() {
    whenever(
      dynamicServicesClient.getDataFromService(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
    ).thenThrow(SubjectAccessRequestException("", null, null, null))

    assertThrows<SubjectAccessRequestException> {
      getSubjectAccessRequestDataService.requestDataFromServices(
        services = selectedDpsServicesSingle,
        nomisId = "A1234AA",
        dateTo = dateToFormatted,
        subjectAccessRequest = subjectAccessRequest,
      )
    }

    verify(dynamicServicesClient, times(1)).getDataFromService(
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
    )
    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent("https://test-service.hmpps.service.justice.gov.uk")
    assertAppInsightsRequestExceptionEvent()
  }

  @Test
  fun `get subject access request data returns status 200 with no body`() {
    getSubjectAccessRequestDataService.requestDataFromServices(
      services = selectedDpsServicesSingle,
      nomisId = "A1234AA",
      dateTo = dateToFormatted,
      subjectAccessRequest = subjectAccessRequest,
    )

    verify(dynamicServicesClient, times(1)).getDataFromService(
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
      anyOrNull(),
    )
    verifyAppInsightsTrackEventIsCalled(2)
    assertAppInsightsRequestStartedEvent("https://test-service.hmpps.service.justice.gov.uk")
    assertAppInsightsRequestNoDataEvent()
  }

  private fun verifyAppInsightsTrackEventIsCalled(times: Int) {
    verify(telemetryClient, times(times))
      .trackEvent(
        appInsightsEventNameCaptor.capture(),
        appInsightsPropertiesCaptor.capture(),
        appInsightsMetricsCaptor.capture(),
      )
  }

  private fun assertAppInsightsRequestStartedEvent(url: String = serviceOneMockApi.baseUrl()) {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
    assertThat(appInsightsEventNameCaptor.allValues[0]).isEqualTo("ServiceDataRequestStarted")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(1)
    assertThat(appInsightsPropertiesCaptor.allValues[0]).containsExactlyInAnyOrderEntriesOf(
      mapOf(
        "sarId" to subjectAccessRequest.sarCaseReferenceNumber,
        "UUID" to subjectAccessRequest.id.toString(),
        "serviceURL" to url,
        "contextId" to subjectAccessRequest.contextId.toString(),
      ),
    )
  }

  private fun assertAppInsightsRequestCompleteEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestComplete")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsPropertiesCaptor.allValues[1])
      .containsOnlyKeys("sarId", "UUID", "serviceURL", "eventTime", "responseSize", "responseStatus", "contextId")
  }

  private fun assertAppInsightsRequestExceptionEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestException")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsPropertiesCaptor.allValues[1])
      .containsOnlyKeys(
        "sarId",
        "UUID",
        "serviceURL",
        "eventTime",
        "responseSize",
        "responseStatus",
        "errorMessage",
        "contextId",
      )
  }

  private fun assertAppInsightsRequestNoDataEvent() {
    assertThat(appInsightsEventNameCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsEventNameCaptor.allValues[1]).isEqualTo("ServiceDataRequestNoData")

    assertThat(appInsightsPropertiesCaptor.allValues).hasSizeGreaterThanOrEqualTo(2)
    assertThat(appInsightsPropertiesCaptor.allValues[1])
      .containsOnlyKeys("sarId", "UUID", "serviceURL", "eventTime", "responseSize", "responseStatus", "contextId")
  }

  private val selectedDpsServicesSingle = listOf(
    DpsService(
      name = "hmpps-test-service",
      businessName = "HMPPS Test Service",
      orderPosition = 1,
      url = "https://test-service.hmpps.service.justice.gov.uk",
    ),
  )
}
