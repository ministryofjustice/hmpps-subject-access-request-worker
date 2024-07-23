package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import io.kotest.core.spec.style.DescribeSpec
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsServices
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
        val serviceDetailsObject = DpsServices(mutableListOf(DpsService(name = "fake-hmpps-prisoner-search", businessName = "Fake HMPPS Prisoner Search", orderPosition = 1, url = "https://fake-prisoner-search.prison.service.justice.gov.uk")))

        getSubjectAccessRequestDataService.execute(services = serviceDetailsObject, nomisId = "A1234AA", dateTo = dateToFormatted)

        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
      }

      it("uses the service name as a content key if the business name is not present") {
        val serviceDetailsObject = DpsServices(mutableListOf(DpsService(name = "fake-hmpps-prisoner-search", businessName = "Fake HMPPS Prisoner Search", orderPosition = 1, url = "https://fake-prisoner-search.prison.service.justice.gov.uk"), DpsService(name = "fake-hmpps-prisoner-search-indexer", businessName = null, orderPosition = null, url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk")))
        val expectedResponseObject = mapOf("Fake HMPPS Prisoner Search" to mapOf<String, Any>("fake-prisoner-search-property" to emptyMap<String, Any>()), "fake-hmpps-prisoner-search-indexer" to mapOf<String, Any>("fake-indexer-property" to emptyMap<String, Any>()))

        val responseObject = getSubjectAccessRequestDataService.execute(services = serviceDetailsObject, nomisId = "A1234AA", dateTo = dateToFormatted)

        Assertions.assertThat(responseObject).isEqualTo(expectedResponseObject)
      }

//      it("calls the gateway separately for each service given") {
//        getSubjectAccessRequestDataService.execute(services = mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk", "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"), nomisId = "A1234AA", dateTo = dateToFormatted)
//
//        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
//        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
//      }
//
//      it("returns upstream API response data with data mapped to API from which it was retrieved") {
//        val response = getSubjectAccessRequestDataService.execute(services = mutableMapOf("fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk", "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"), nomisId = "A1234AA", dateTo = dateToFormatted)
//
//        response.keys.shouldBe(setOf("fake-hmpps-prisoner-search", "fake-hmpps-prisoner-search-indexer"))
//        response["fake-hmpps-prisoner-search"].toString().shouldContain("fake-prisoner-search-property")
//        response["fake-hmpps-prisoner-search-indexer"].toString().shouldContain("fake-indexer-property")
//      }
    }
  },
)
