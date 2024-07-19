package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsService
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.DpsServices

class ConfigOrderHelperTest {
  val configOrderHelper = ConfigOrderHelper()

  @Nested
  inner class ExtractServicesConfig {

    @Test
    fun `extractServicesConfig reads config from a yaml file and creates a list of service details`() = runTest {
      val testServiceDetails = configOrderHelper.extractServicesConfig("src/test/resources/services-config-test.yaml")

      Assertions.assertThat(testServiceDetails).isInstanceOf(DpsServices::class.java)
      Assertions.assertThat(testServiceDetails?.dpsServices?.get(0)).isInstanceOf(DpsService::class.java)
      Assertions.assertThat(testServiceDetails?.dpsServices?.get(0)?.name).isEqualTo("test-dps-service-1")
    }

  }
}