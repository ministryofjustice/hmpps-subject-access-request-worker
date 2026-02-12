package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory.PRISON
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory.PROBATION
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import java.util.stream.Stream

class ServiceConfigurationComparatorTest {

  private val comparator = ServiceConfigurationComparator()

  @Test
  fun `should return empty with input is empty`() {
    assertThat(emptyList<ServiceConfiguration>().sortedWith(comparator)).isEmpty()
  }

  @ParameterizedTest
  @MethodSource("testCases")
  fun `should sort input list to the expected order`(tc: TestCase) {
    val actual = tc.input.sortedWith(comparator)

    assertThat(actual).isNotEmpty
    assertThat(actual).hasSize(tc.expected.size)
    assertThat(actual).containsExactly(*tc.expected.toTypedArray())
  }

  companion object {
    private val g1 = serviceConfig("G1", PROBATION)
    private val g2 = serviceConfig("G2", PROBATION)
    private val g3 = serviceConfig("G3", PRISON)
    private val prison1 = serviceConfig("PriA1", PRISON)
    private val prison2 = serviceConfig("PriA2", PRISON)
    private val prison3 = serviceConfig("PriA3", PRISON)
    private val probationX = serviceConfig("ProX", PROBATION)
    private val probationY = serviceConfig("ProY", PROBATION)
    private val probationZ = serviceConfig("ProZ", PROBATION)

    private fun serviceConfig(
      name: String,
      category: ServiceCategory,
    ): ServiceConfiguration = ServiceConfiguration(
      serviceName = "hmpps-$name",
      label = name,
      url = "",
      category = category,
      enabled = true,
    )

    data class TestCase(
      val input: List<ServiceConfiguration>,
      val expected: List<ServiceConfiguration>,
      val description: String,
    ) {
      override fun toString(): String = "$description: input=${input.joinToString(",") { it.label }}, " +
        "expected=${expected.joinToString(",") { it.label }}"
    }

    @JvmStatic
    fun testCases(): Stream<TestCase> = Stream.of(
      TestCase(
        input = listOf(g2, g1, g3),
        expected = listOf(g1, g2, g3),
        description = "should correctly sort G services",
      ),
      TestCase(
        input = listOf(prison2, prison1, prison3),
        expected = listOf(prison1, prison2, prison3),
        description = "should correctly sort Prison services",
      ),
      TestCase(
        input = listOf(probationY, probationX, probationZ),
        expected = listOf(probationX, probationY, probationZ),
        description = "should correctly sort Probation services",
      ),
      TestCase(
        input = listOf(prison2, g3, g1, prison3, g2, prison1),
        expected = listOf(g1, g2, g3, prison1, prison2, prison3),
        description = "should correctly sort G and Prison services",
      ),
      TestCase(
        input = listOf(probationX, g3, g1, probationZ, g2, probationY),
        expected = listOf(g1, g2, g3, probationX, probationY, probationZ),
        description = "should correctly sort G and Probation services",
      ),
      TestCase(
        input = listOf(probationX, prison3, prison1, probationZ, prison2, probationY),
        expected = listOf(prison1, prison2, prison3, probationX, probationY, probationZ),
        description = "should correctly sort Prison and Probation services",
      ),
      TestCase(
        input = listOf(probationX, prison2, g3, prison1, g1, probationZ, g2, probationY, prison3),
        expected = listOf(g1, g2, g3, prison1, prison2, prison3, probationX, probationY, probationZ),
        description = "should correctly sort G, Prison and Probation services",
      ),
    )
  }
}
