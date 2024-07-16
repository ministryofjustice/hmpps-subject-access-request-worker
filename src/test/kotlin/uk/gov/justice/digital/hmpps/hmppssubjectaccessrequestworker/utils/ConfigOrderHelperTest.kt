package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.DocumentStorageGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.Status
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services.GeneratePdfService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services.GetSubjectAccessRequestDataService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services.SubjectAccessRequestWorkerService
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class ConfigOrderHelperTest {
  private val mockSarGateway = Mockito.mock(SubjectAccessRequestGateway::class.java)
  private val mockGetSubjectAccessRequestDataService = Mockito.mock(GetSubjectAccessRequestDataService::class.java)
  private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
  private val dateFrom = "02/01/2023"
  private val dateFromFormatted = LocalDate.parse(dateFrom, formatter)
  private val dateTo = "02/01/2024"
  private val dateToFormatted = LocalDate.parse(dateTo, formatter)
  private val requestTime = LocalDateTime.now()
  private val documentGateway: DocumentStorageGateway = Mockito.mock(DocumentStorageGateway::class.java)
  private val sampleSAR = SubjectAccessRequest(
    id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
    status = Status.Pending,
    dateFrom = dateFromFormatted,
    dateTo = dateToFormatted,
    sarCaseReferenceNumber = "1234abc",
    services = "fake-hmpps-prisoner-search, https://fake-prisoner-search.prison.service.justice.gov.uk,fake-hmpps-prisoner-search-indexer, https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
    nomisId = null,
    ndeliusCaseReferenceId = "1",
    requestedBy = "aName",
    requestDateTime = requestTime,
    claimAttempts = 0,
  )
  private val telemetryClient = Mockito.mock(TelemetryClient::class.java)
  private val mockGeneratePdfService = Mockito.mock(GeneratePdfService::class.java)
  val subjectAccessRequestWorkerService = SubjectAccessRequestWorkerService(mockSarGateway, mockGetSubjectAccessRequestDataService, documentGateway, mockGeneratePdfService, "http://localhost:8080", telemetryClient)
  val configOrderHelper = ConfigOrderHelper()
  @Nested
  inner class GetOrderedServicesMap {
    @Test
    fun `getOrderedServicesMap returns a map of service URLs in the right order`() = runTest {
      val expectedOrderedSarUrlMap = mutableMapOf("0" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk", "1" to "https://fake-prisoner-search.prison.service.justice.gov.uk")
      val orderedSarUrlList = subjectAccessRequestWorkerService.getOrderedServicesMap(sampleSAR)

      Assertions.assertThat(orderedSarUrlList).isEqualTo(expectedOrderedSarUrlMap)
    }
  }

  @Nested
  inner class CreateOrderedServiceUrlList {
    val orderedUrlList = listOf("test1.com", "test2.com")
    val sarUrlList = mutableListOf("test2.com", "test1.com")

    @Test
    fun `createOrderedServiceUrlList returns a list`() = runTest {
      val orderedSarUrlList = configOrderHelper.createOrderedServiceUrlList(orderedUrlList, sarUrlList)

      Assertions.assertThat(orderedSarUrlList).isInstanceOf(List::class.java)
    }

    @Test
    fun `createOrderedServiceUrlList puts the SAR URL list into the order of the ordered URL list`() = runTest {
      val expectedOrderedSarUrlList = listOf("test1.com", "test2.com")

      val orderedSarUrlList = configOrderHelper.createOrderedServiceUrlList(orderedUrlList, sarUrlList)

      Assertions.assertThat(orderedSarUrlList).isEqualTo(expectedOrderedSarUrlList)
    }

    @Test
    fun `createOrderedServiceUrlList adds SAR URLs that do not appear in the ordered URL list onto the end of the orderedSarUrlList`() = runTest {
      val sarUrlList = mutableListOf("test2.com", "test1.com", "newly-added-service.com")
      val expectedOrderedSarUrlList = listOf("test1.com", "test2.com", "newly-added-service.com")

      val orderedSarUrlList = configOrderHelper.createOrderedServiceUrlList(orderedUrlList, sarUrlList)

      Assertions.assertThat(orderedSarUrlList).isEqualTo(expectedOrderedSarUrlList)
    }
  }

  @Nested
  inner class ExtractServicesConfig {

    @Test
    fun `extractServicesConfig reads URLs from a file`() = runTest {
      val tmpFile = File("test1.txt")
      tmpFile.appendText("test1.com\n")
      tmpFile.appendText("test2.com\n")
      tmpFile.appendText("newly-added-service.com\n")
      val expectedUrlList = listOf("test1.com", "test2.com", "newly-added-service.com")

      val orderedSarUrlList = configOrderHelper.extractServicesConfig("test1.txt")

      Assertions.assertThat(orderedSarUrlList).isEqualTo(expectedUrlList)
      tmpFile.delete()
    }
  }
}