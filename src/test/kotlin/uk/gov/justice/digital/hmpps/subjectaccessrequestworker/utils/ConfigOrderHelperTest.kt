package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfig

class ConfigOrderHelperTest {
  val configOrderHelper = ConfigOrderHelper()

  @Nested
  inner class ExtractServicesConfig {

    @Test
    fun `extractServicesConfig reads config from a yaml file and creates a list of service details`() {
      val testServiceDetails = configOrderHelper.extractServicesConfig("services-config-test.yaml")

      Assertions.assertThat(testServiceDetails).isInstanceOf(ServiceConfig::class.java)
      Assertions.assertThat(testServiceDetails?.dpsServices?.get(0)).isInstanceOf(DpsService::class.java)
      Assertions.assertThat(testServiceDetails?.dpsServices?.get(0)?.name).isEqualTo("test-dps-service-1")
    }

    @Nested
    inner class GetDpsServices {
      @Test
      fun `getDpsServices returns a list of DPS service objects`() {
        val testServicesMap = mapOf(
          "fake-hmpps-prisoner-search" to "https://fake-prisoner-search.prison.service.justice.gov.uk",
          "fake-hmpps-prisoner-search-indexer" to "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk",
        )
        val expectedDpsServices = listOf(
          DpsService(name = "fake-hmpps-prisoner-search", url = "https://fake-prisoner-search.prison.service.justice.gov.uk"),
          DpsService(name = "fake-hmpps-prisoner-search-indexer", url = "https://fake-prisoner-search-indexer.prison.service.justice.gov.uk"),
        )

        val testDpsServices = configOrderHelper.getDpsServices(testServicesMap)

        Assertions.assertThat(testDpsServices[0].name).isEqualTo(expectedDpsServices[0].name)
        Assertions.assertThat(testDpsServices[0].url).isEqualTo(expectedDpsServices[0].url)
        Assertions.assertThat(testDpsServices[1].name).isEqualTo(expectedDpsServices[1].name)
        Assertions.assertThat(testDpsServices[1].url).isEqualTo(expectedDpsServices[1].url)
      }
    }
  }
}
