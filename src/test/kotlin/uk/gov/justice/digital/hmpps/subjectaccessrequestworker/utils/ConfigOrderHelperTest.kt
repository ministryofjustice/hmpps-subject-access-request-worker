package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfig

class ConfigOrderHelperTest {
  val configOrderHelper = ConfigOrderHelper(
    "https://service-g1.example.com",
    "https://service-g2.example.com",
    "https://service-g3.example.com",
  )

  @Nested
  inner class ExtractServicesConfig {

    @Test
    fun `extractServicesConfig reads config from a yaml file and creates a list of service details`() {
      val testServiceDetails = configOrderHelper.extractServicesConfig("services-config-test.yaml")

      assertThat(testServiceDetails).isInstanceOf(ServiceConfig::class.java)
      assertThat(testServiceDetails?.dpsServices?.get(0)).isInstanceOf(DpsService::class.java)
      assertThat(testServiceDetails?.dpsServices?.get(0)?.name).isEqualTo("test-dps-service-1")
    }

    @Nested
    inner class GetDpsServices {
      @Test
      fun `getDpsServices returns a list of DPS service objects`() {
        val testServicesMap = mapOf(
          "example service 1" to "https://example-one.hmpps.service.justice.gov.uk",
          "example service 2" to "https://example-two.hmpps.service.justice.gov.uk",
        )

        val testDpsServices = configOrderHelper.getDpsServices(testServicesMap)

        assertThat(testDpsServices[0].name).isEqualTo("example service 1")
        assertThat(testDpsServices[0].url).isEqualTo("https://example-one.hmpps.service.justice.gov.uk")
        assertThat(testDpsServices[1].name).isEqualTo("example service 2")
        assertThat(testDpsServices[1].url).isEqualTo("https://example-two.hmpps.service.justice.gov.uk")
      }
    }
  }

  @Test
  fun `getDpsServices swaps G1, G2, and G3 values`() {
    val testServicesMap = mapOf(
      "G1" to "G1",
      "G2" to "G2",
      "G3" to "G3",
      "other-service" to "https://other-service.example.com",
    )

    val testDpsServices = configOrderHelper.getDpsServices(testServicesMap)

    assertThat(testDpsServices).hasSize(4)
    assertThat(testDpsServices[0].url).isEqualTo("https://service-g1.example.com")
    assertThat(testDpsServices[1].url).isEqualTo("https://service-g2.example.com")
    assertThat(testDpsServices[2].url).isEqualTo("https://service-g3.example.com")
    assertThat(testDpsServices[3].url).isEqualTo("https://other-service.example.com")
  }
}
