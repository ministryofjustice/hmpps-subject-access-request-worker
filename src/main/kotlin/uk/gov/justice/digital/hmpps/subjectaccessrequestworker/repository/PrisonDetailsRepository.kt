package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.PrisonDetail

@Repository
interface PrisonDetailsRepository : JpaRepository<PrisonDetail, String> {
  fun findByPrisonId(prisonId: String): PrisonDetail?
}
