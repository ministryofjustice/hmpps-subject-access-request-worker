package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.utils

import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceCategory
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration

/**
 * Comparator sorts [ServiceConfiguration] based on [ServiceConfiguration.label] for report ordering:
 * - G1,
 * - G2,
 * - G3,
 * - Prison services alphabetically
 * - Probation services alphabetically
 */
class ServiceConfigurationComparator : Comparator<ServiceConfiguration> {
  override fun compare(
    serviceA: ServiceConfiguration,
    serviceB: ServiceConfiguration,
  ): Int {
    val priorityRankA = rankByPriority(serviceA)
    val priorityRankB = rankByPriority(serviceB)
    if (priorityRankA != priorityRankB) {
      return priorityRankA.compareTo(priorityRankB)
    }

    val categoryARank = rankByServiceCategory(serviceA)
    val categoryBRank = rankByServiceCategory(serviceB)
    if (categoryARank != categoryBRank) return categoryARank.compareTo(categoryBRank)

    return serviceA.label.compareTo(serviceB.label)
  }

  private fun rankByPriority(s: ServiceConfiguration): Int = when (s.label) {
    "G1" -> 0
    "G2" -> 1
    "G3" -> 2
    else -> Int.MAX_VALUE
  }

  private fun rankByServiceCategory(s: ServiceConfiguration): Int = when (s.category) {
    ServiceCategory.PRISON -> 1
    ServiceCategory.PROBATION -> 2
    else -> Int.MAX_VALUE
  }
}
