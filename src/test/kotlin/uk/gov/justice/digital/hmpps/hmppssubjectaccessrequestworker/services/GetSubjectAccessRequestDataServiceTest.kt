package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.mockito.Mockito
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val dateTo = "30/01/2023"
    val dateToFormatted = LocalDate.parse(dateTo, formatter)

    beforeEach {
      Mockito.reset(mockGenericHmppsApiGateway)

      whenever(mockGenericHmppsApiGateway.getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)).thenReturn(
        """
          {
            "content": {
              "fake-prisoner-search-property": {}
            }
          }
        """.trimIndent(),
      )

      whenever(mockGenericHmppsApiGateway.getSarData(serviceUrl = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)).thenReturn(
        """
          {
            "content": {
              "fake-indexer-property": {}
            }
          }
        """.trimIndent(),
      )
    }

    describe("getSubjectAccessRequestData") {
      it("calls getSarData with given arguments, including service URL") {
        val response = getSubjectAccessRequestDataService.execute(services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk", nomisId = "A1234AA", dateTo = dateToFormatted)

        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
      }

      it("calls the gateway separately for each service given") {
        val response = getSubjectAccessRequestDataService.execute(services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk,fake-hmpps-prisoner-search-indexer, https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", nomisId = "A1234AA", dateTo = dateToFormatted)

        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
        verify(mockGenericHmppsApiGateway, Mockito.times(1)).getSarData(serviceUrl = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", prn = "A1234AA", dateTo = dateToFormatted)
      }

      it("returns upstream API response data with data mapped to API from which it was retrieved") {
        val response = getSubjectAccessRequestDataService.execute(services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk,fake-hmpps-prisoner-search-indexer, https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", nomisId = "A1234AA", dateTo = dateToFormatted)

        response.keys.shouldBe(setOf("fake-hmpps-prisoner-search", "fake-hmpps-prisoner-search-indexer"))
        response["fake-hmpps-prisoner-search"].toString().shouldContain("fake-prisoner-search-property")
        response["fake-hmpps-prisoner-search-indexer"].toString().shouldContain("fake-indexer-property")
      }
    }
  },
)