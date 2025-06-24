package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

const val BACKLOG_REQUEST_LOCK_TIMEOUT = "3000"

@Repository
interface BacklogRequestRepository : JpaRepository<BacklogRequest, UUID> {

  fun countByVersion(version: String): Long

  fun countByVersionAndStatus(version: String, status: BacklogRequestStatus): Long

  fun countByVersionAndStatusAndDataHeld(version: String, status: BacklogRequestStatus, dataHeld: Boolean): Long

  fun findByVersionOrderByCreatedAt(version: String): MutableList<BacklogRequest>

  @Lock(LockModeType.PESSIMISTIC_READ)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = BACKLOG_REQUEST_LOCK_TIMEOUT)])
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  fun deleteBacklogRequestByVersion(version: String): Int

  @Query("SELECT DISTINCT (b.version) FROM BacklogRequest b ORDER BY b.version")
  fun findDistinctVersions(): Set<String>

  @Lock(LockModeType.PESSIMISTIC_READ)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = BACKLOG_REQUEST_LOCK_TIMEOUT)])
  fun findByIdAndStatus(@Param("id") id: UUID, status: BacklogRequestStatus): BacklogRequest?

  @Lock(LockModeType.PESSIMISTIC_READ)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = BACKLOG_REQUEST_LOCK_TIMEOUT)])
  @Query(
    "SELECT b FROM BacklogRequest b " +
      "WHERE b.status != 'COMPLETE' " +
      "AND (b.claimDateTime IS NULL OR b.claimDateTime < :backOffThreshold) " +
      "ORDER BY b.createdAt " +
      "LIMIT 1",
  )
  fun getNextToProcess(
    @Param("backOffThreshold") backOffThreshold: LocalDateTime = now().minusMinutes(30),
  ): BacklogRequest?

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    "UPDATE BacklogRequest br SET br.claimDateTime = :currentDateTime " +
      "WHERE br.id = :id " +
      "AND br.status = 'PENDING' " +
      "AND (br.claimDateTime IS NULL OR br.claimDateTime < :backOffThreshold)",
  )
  fun updateClaimDateTime(
    @Param("id") id: UUID,
    @Param("backOffThreshold") backOffThreshold: LocalDateTime = now().minusMinutes(30),
    @Param("currentDateTime") currentDateTime: LocalDateTime = now(),
  ): Int

  @Lock(LockModeType.PESSIMISTIC_READ)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = BACKLOG_REQUEST_LOCK_TIMEOUT)])
  @Query(
    "SELECT b FROM BacklogRequest b " +
      "WHERE b.id = :id " +
      "AND " +
      "(SELECT COUNT(DISTINCT s.serviceName) FROM ServiceSummary s WHERE s.backlogRequest.id = :id AND s.status = 'COMPLETE')" +
      " = " +
      "(SELECT COUNT(DISTINCT cfg.serviceName) FROM ServiceConfiguration cfg)",
  )
  fun findCompleteRequestOrNull(@Param("id") id: UUID): BacklogRequest?

  @Query(
    "SELECT b FROM BacklogRequest b " +
      "WHERE b.id = :id " +
      "AND EXISTS (" +
      "SELECT s FROM ServiceSummary s WHERE s.backlogRequest.id = :id AND s.dataHeld is true AND s.status = 'COMPLETE'" +
      ")",
  )
  fun findDataHeldByIdOrNull(@Param("id") id: UUID): BacklogRequest?
}
