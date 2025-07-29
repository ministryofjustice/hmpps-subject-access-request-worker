package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import java.util.UUID

@Repository
interface ServiceConfigurationRepository : JpaRepository<ServiceConfiguration, UUID> {
  fun findByServiceName(serviceName: String): ServiceConfiguration?

  @Query("UPDATE ServiceConfiguration s SET s.enabled = :enabled WHERE s.id = :id")
  @Modifying
  fun updateEnabledById(@Param("id") id: UUID, @Param("enabled") enabled: Boolean): Int

  @Query("SELECT s FROM ServiceConfiguration s WHERE s.enabled is TRUE ORDER BY s.order ASC")
  fun findEnabled(): List<ServiceConfiguration>?
}
