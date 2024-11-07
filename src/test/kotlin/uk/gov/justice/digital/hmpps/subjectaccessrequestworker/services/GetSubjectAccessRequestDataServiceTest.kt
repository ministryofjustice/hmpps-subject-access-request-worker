package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.GenericHmppsApiGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class GetSubjectAccessRequestDataServiceTest {

  private val genericHmppsApiGateway: GenericHmppsApiGateway = mock()
  private val getSubjectAccessRequestDataService = GetSubjectAccessRequestDataService(genericHmppsApiGateway)
  private val dateToFormatted = LocalDate.parse("30/01/2023", DateTimeFormatter.ofPattern("dd/MM/yyyy"))

  @Test
  fun `getSubjectAccessRequestData calls getSarData with given arguments, including service URL`() {
    val selectedDpsServices = listOf(
      DpsService(
        name = "hmpps-test-service",
        businessName = "HMPPS Test Service",
        orderPosition = 1,
        url = "https://test-service.hmpps.service.justice.gov.uk",
      ),
    )

    getSubjectAccessRequestDataService.execute(
      services = selectedDpsServices,
      nomisId = "A1234AA",
      dateTo = dateToFormatted,
    )

    verify(
      genericHmppsApiGateway,
      times(1),
    ).getSarData(
      serviceUrl = "https://test-service.hmpps.service.justice.gov.uk",
      prn = "A1234AA",
      dateTo = dateToFormatted,
    )
  }

  @Test
  fun `getSubjectAccessRequestData uses the service name as a content key if the business name is not present`() {
    whenever(
      genericHmppsApiGateway.getSarData(
        serviceUrl = anyOrNull(),
        prn = anyOrNull(),
        crn = anyOrNull(),
        dateFrom = anyOrNull(),
        dateTo = anyOrNull(),
        subjectAccessRequest = anyOrNull(),
      ),
    ).thenReturn(
      mapOf(
        "content" to mapOf<String, Any>("prisoner-test-property-business-name" to emptyMap<String, Any>()),
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

    val response = getSubjectAccessRequestDataService.execute(
      services = selectedDpsServices,
      nomisId = "A1234AA",
      dateTo = dateToFormatted,
    )

    assertThat(response[0].content.toString()).isEqualTo("{prisoner-test-property-business-name={}}")
    verify(genericHmppsApiGateway, times(1)).getSarData(serviceUrl = "https://test-service.hmpps.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
    verify(genericHmppsApiGateway, times(1)).getSarData(serviceUrl = "https://test-service-2.hmpps.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
  }

  @Test
  fun `getSubjectAccessRequestData  returns upstream API response data in the correct order`() {
    val selectedDpsServices = mutableListOf(
      DpsService(name = "hmpps-service", businessName = null, orderPosition = 2, url = "https://srevice.hmpps.service.justice.gov.uk"),
      DpsService(name = "hmpps-service-2", businessName = null, orderPosition = 3, url = "https://service-2.hmpps.service.justice.gov.uk"),
      DpsService(name = "hmpps-service-test", businessName = null, orderPosition = 1, url = "https://service-test.hmpps.service.justice.gov.uk"),
    )

    val orderedDpsServices = getSubjectAccessRequestDataService.order(selectedDpsServices)

    assertThat(orderedDpsServices[0].orderPosition).isEqualTo(1)
    assertThat(orderedDpsServices[1].orderPosition).isEqualTo(2)
    assertThat(orderedDpsServices[2].orderPosition).isEqualTo(3)
  }

  @Test
  fun `getSubjectAccessRequestData puts services with no order position last`() {
    val selectedDpsServices = mutableListOf(
      DpsService(name = "service-B", businessName = null, orderPosition = null, url = "https://service-b.prison.service.justice.gov.uk"),
      DpsService(name = "service-A", businessName = null, orderPosition = 2, url = "https://service-a.prison.service.justice.gov.uk"),
      DpsService(name = "service-C", businessName = null, orderPosition = 1, url = "https://service-c.prison.service.justice.gov.uk"),
    )

    val orderedDpsServices = getSubjectAccessRequestDataService.order(selectedDpsServices)

    assertThat(orderedDpsServices[0].orderPosition).isEqualTo(1)
    assertThat(orderedDpsServices[1].orderPosition).isEqualTo(2)
    assertThat(orderedDpsServices[2].orderPosition).isEqualTo(null)
  }

  @Test
  fun `getSubjectAccessRequestData sorts services with no order position alphabetically by name`() {
    val selectedDpsServices = mutableListOf(
      DpsService(name = "service-B", businessName = null, orderPosition = null, url = "https://service-b.prison.service.justice.gov.uk"),
      DpsService(name = "service-A", businessName = null, orderPosition = null, url = "https://service-a.prison.service.justice.gov.uk"),
      DpsService(name = "service-C", businessName = null, orderPosition = null, url = "https://service-c.prison.service.justice.gov.uk"),
    )

    val orderedDpsServices = getSubjectAccessRequestDataService.order(selectedDpsServices)

    Assertions.assertThat(orderedDpsServices[0].name).isEqualTo("service-A")
    Assertions.assertThat(orderedDpsServices[1].name).isEqualTo("service-B")
    Assertions.assertThat(orderedDpsServices[2].name).isEqualTo("service-C")
  }
}
