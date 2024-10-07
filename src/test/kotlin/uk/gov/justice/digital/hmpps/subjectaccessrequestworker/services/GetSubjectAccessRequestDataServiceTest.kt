package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.gateways.GenericHmppsApiGateway
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ActiveProfiles("test")
@ContextConfiguration(
  initializers = [ConfigDataApplicationContextInitializer::class],
  classes = [(GetSubjectAccessRequestDataService::class)],
)
class GetSubjectAccessRequestDataServiceTest(
  getSubjectAccessRequestDataService: GetSubjectAccessRequestDataService,
  @MockBean val mockGenericHmppsApiGateway: GenericHmppsApiGateway,
) : DescribeSpec(
  {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dateTo = "30/01/2023"
    val dateToFormatted = LocalDate.parse(dateTo, formatter)

    beforeEach {
      Mockito.reset(mockGenericHmppsApiGateway)

      whenever(mockGenericHmppsApiGateway.getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)).thenReturn(
        mapOf(
          "content" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()),
        ),
      )

      whenever(mockGenericHmppsApiGateway.getSarData(serviceUrl = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)).thenReturn(
        mapOf(
          "content" to mapOf<String, Any>("fake-indexer-property" to emptyMap<String, Any>()),
        ),
      )
    }

    describe("getSubjectAccessRequestData") {
      it("calls getSarData with given arguments, including service URL") {
        val selectedDpsServices = mutableListOf(DpsService(name = "fake-hmpps-prisoner-search", businessName = "Fake HMPPS Prisoner Search", orderPosition = 1, url = "https://fake-prisoner-search.prison.service.justice.gov.uk"))
        getSubjectAccessRequestDataService.execute(services = selectedDpsServices, nomisId = "A1234AA", dateTo = dateToFormatted)

        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
      }

      it("uses the service name as a content key if the business name is not present") {
        val selectedDpsServices = mutableListOf(DpsService(name = "fake-hmpps-prisoner-search", businessName = "Fake HMPPS Prisoner Search", orderPosition = 1, url = "https://fake-prisoner-search.prison.service.justice.gov.uk"), DpsService(name = "fake-hmpps-prisoner-search-indexer", businessName = null, orderPosition = null, url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"))
        val expectedResponseObject = listOf(
          DpsService(
            content = mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()),
          ),
          DpsService(
            content = mapOf<String, Any>("fake-indexer-property" to emptyMap<String, Any>()),
          ),
        )

        val responseObject = getSubjectAccessRequestDataService.execute(services = selectedDpsServices, nomisId = "A1234AA", dateTo = dateToFormatted)

        Assertions.assertThat(responseObject[0].content).isEqualTo(expectedResponseObject[0].content)
      }

      it("calls the gateway separately for each service given") {
        val selectedDpsServices = mutableListOf(DpsService(name = "fake-hmpps-prisoner-search", businessName = null, orderPosition = null, url = "https://fake-prisoner-search.prison.service.justice.gov.uk"), DpsService(name = "fake-hmpps-prisoner-search-indexer", businessName = null, orderPosition = null, url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"))

        getSubjectAccessRequestDataService.execute(services = selectedDpsServices, nomisId = "A1234AA", dateTo = dateToFormatted)

        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
      }

      it("returns upstream API response data with data mapped to API from which it was retrieved") {
        val selectedDpsServices = mutableListOf(DpsService(name = "fake-hmpps-prisoner-search", businessName = null, orderPosition = null, url = "https://fake-prisoner-search.prison.service.justice.gov.uk"), DpsService(name = "fake-hmpps-prisoner-search-indexer", businessName = null, orderPosition = null, url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"))

        val response = getSubjectAccessRequestDataService.execute(services = selectedDpsServices, nomisId = "A1234AA", dateTo = dateToFormatted)

        response[0].name.shouldBe("fake-hmpps-prisoner-search")
        response[1].name.shouldBe("fake-hmpps-prisoner-search-indexer")
        response[0].content.toString().shouldContain("fake-prisoner-search-property")
        response[1].content.toString().shouldContain("fake-indexer-property")
      }

      it("returns upstream API response data in the correct order") {
        val selectedDpsServices = mutableListOf(
          DpsService(name = "fake-hmpps-prisoner-search", businessName = null, orderPosition = 2, url = "https://fake-prisoner-search.prison.service.justice.gov.uk"),
          DpsService(name = "fake-hmpps-prisoner-search-2", businessName = null, orderPosition = 3, url = "https://fake-prisoner-search-2.prison.service.justice.gov.uk"),
          DpsService(name = "fake-hmpps-prisoner-search-indexer", businessName = null, orderPosition = 1, url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"),
        )
        val response = getSubjectAccessRequestDataService.execute(services = selectedDpsServices, nomisId = "A1234AA", dateTo = dateToFormatted)

        response[0].name.shouldBe("fake-hmpps-prisoner-search-indexer")
        response[1].name.shouldBe("fake-hmpps-prisoner-search")
      }
    }

    describe("order") {
      it("sorts services by order position") {
        val selectedDpsServices = mutableListOf(
          DpsService(name = "fake-hmpps-prisoner-search", businessName = null, orderPosition = 2, url = "https://fake-prisoner-search.prison.service.justice.gov.uk"),
          DpsService(name = "fake-hmpps-prisoner-search-2", businessName = null, orderPosition = 3, url = "https://fake-prisoner-search-2.prison.service.justice.gov.uk"),
          DpsService(name = "fake-hmpps-prisoner-search-indexer", businessName = null, orderPosition = 1, url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"),
        )

        val orderedDpsServices = getSubjectAccessRequestDataService.order(selectedDpsServices)

        Assertions.assertThat(orderedDpsServices[0].orderPosition).isEqualTo(1)
        Assertions.assertThat(orderedDpsServices[1].orderPosition).isEqualTo(2)
        Assertions.assertThat(orderedDpsServices[2].orderPosition).isEqualTo(3)
      }

      it("puts services with no order position last") {
        val selectedDpsServices = mutableListOf(
          DpsService(name = "fake-hmpps-prisoner-search", businessName = null, orderPosition = null, url = "https://fake-prisoner-search.prison.service.justice.gov.uk"),
          DpsService(name = "fake-hmpps-prisoner-search-2", businessName = null, orderPosition = 2, url = "https://fake-prisoner-search-2.prison.service.justice.gov.uk"),
          DpsService(name = "fake-hmpps-prisoner-search-indexer", businessName = null, orderPosition = 1, url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"),
        )

        val orderedDpsServices = getSubjectAccessRequestDataService.order(selectedDpsServices)

        Assertions.assertThat(orderedDpsServices[0].orderPosition).isEqualTo(1)
        Assertions.assertThat(orderedDpsServices[1].orderPosition).isEqualTo(2)
        Assertions.assertThat(orderedDpsServices[2].orderPosition).isEqualTo(null)
      }

      it("sorts services with no order position alphabetically by name") {
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
  },
)
