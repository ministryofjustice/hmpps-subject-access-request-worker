package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import java.util.UUID

@Repository
interface ServiceConfigurationRepository : JpaRepository<ServiceConfiguration, UUID> {
  fun findByServiceName(serviceName: String): ServiceConfiguration?
}
