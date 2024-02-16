package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import io.kotest.core.spec.style.DescribeSpec
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.GenericHmppsApiGateway
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
    beforeEach {
      Mockito.reset(mockGenericHmppsApiGateway)
    }

    describe("getSubjectAccessRequestData") {
      it("calls getSarData with given arguments, including service URL") {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val dateTo = "30/01/2023"
        val dateToFormatted = LocalDate.parse(dateTo, formatter)


        val response = getSubjectAccessRequestDataService.execute(services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk", nomisId = "AA123A", dateTo = dateToFormatted)

        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "AA123A", dateTo = dateToFormatted)
      }

      it("calls the gateway separately for each service given") {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val dateTo = "30/01/2023"
        val dateToFormatted = LocalDate.parse(dateTo, formatter)


        val response = getSubjectAccessRequestDataService.execute(services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk,fake-hmpps-prisoner-search-indexer, https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", nomisId = "AA123A", dateTo = dateToFormatted)

        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "AA123A", dateTo = dateToFormatted)
        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", prn = "AA123A", dateTo = dateToFormatted)
      }
    }
  },
)
