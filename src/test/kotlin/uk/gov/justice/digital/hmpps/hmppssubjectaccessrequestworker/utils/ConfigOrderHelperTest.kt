package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.utils

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class ConfigOrderHelperTest {
  val configOrderHelper = ConfigOrderHelper()

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