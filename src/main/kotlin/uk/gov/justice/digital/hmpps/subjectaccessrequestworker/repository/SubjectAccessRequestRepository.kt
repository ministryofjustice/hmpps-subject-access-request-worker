package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.SubjectAccessRequest
import java.time.LocalDateTime
import java.util.*

const val LOCK_TIMEOUT = "3000"

@Repository
interface SubjectAccessRequestRepository : JpaRepository<SubjectAccessRequest, UUID>, JpaSpecificationExecutor<SubjectAccessRequest> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = LOCK_TIMEOUT)])
  override fun findById(id: UUID): Optional<SubjectAccessRequest>

  @Lock(LockModeType.PESSIMISTIC_READ)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = LOCK_TIMEOUT)])
  @Query(
    "SELECT report FROM SubjectAccessRequest report " +
      "WHERE (report.status = 'Pending' " +
      "AND report.claimAttempts = 0) " +
      "OR (report.status = 'Pending' " +
      "AND report.claimAttempts > 0 " +
      "AND report.claimDateTime < :claimDateTime)",
  )
  fun findUnclaimed(@Param("claimDateTime") claimDateTime: LocalDateTime): List<SubjectAccessRequest?>

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    "UPDATE SubjectAccessRequest report " +
      "SET report.claimDateTime = :currentTime, report.claimAttempts = report.claimAttempts + 1" +
      "WHERE (report.status = 'Pending' AND report.id = :id AND report.claimDateTime < :releaseThreshold) " +
      "OR (report.status = 'Pending' AND report.id = :id AND report.claimDateTime IS NULL)",
  )
  fun updateClaimDateTimeAndClaimAttemptsIfBeforeThreshold(
    @Param("id") id: UUID,
    @Param("releaseThreshold") releaseThreshold: LocalDateTime,
    @Param("currentTime") currentTime: LocalDateTime,
  ): Int
}
