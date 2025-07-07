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
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceSummary
import java.util.UUID

@Repository
interface ServiceSummaryRepository : JpaRepository<ServiceSummary, UUID> {

  @Lock(LockModeType.PESSIMISTIC_READ)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = BACKLOG_REQUEST_LOCK_TIMEOUT)])
  @Query(
    "SELECT cfg FROM ServiceConfiguration cfg " +
      "WHERE NOT EXISTS (" +
      "SELECT summary.serviceName FROM BacklogRequest request " +
      "INNER JOIN ServiceSummary summary ON summary.backlogRequest.id = request.id " +
      "WHERE request.id = :backlogRequestId " +
      "AND summary.serviceName = cfg.serviceName " +
      "AND summary.status = 'COMPLETE'" +
      ") " +
      "ORDER BY cfg.order",
  )
  fun getPendingServiceSummariesForRequestId(@Param("backlogRequestId") id: UUID): List<ServiceConfiguration>

  @Lock(LockModeType.PESSIMISTIC_READ)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = BACKLOG_REQUEST_LOCK_TIMEOUT)])
  fun existsByBacklogRequestIdAndServiceName(
    @Param("backlogRequestId") id: UUID,
    @Param("serviceName") serviceName: String,
  ): Boolean

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = BACKLOG_REQUEST_LOCK_TIMEOUT)])
  fun findOneByBacklogRequestIdAndServiceName(backlogRequestId: UUID, serviceName: String): ServiceSummary?

  @Lock(LockModeType.PESSIMISTIC_READ)
  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = BACKLOG_REQUEST_LOCK_TIMEOUT)])
  fun countByBacklogRequestIdAndDataHeld(backlogRequestId: UUID, dataHeld: Boolean = true): Long

  /**
   * Only used in Integration tests.
   */
  fun findOneByBacklogRequestIdAndServiceNameAndStatus(
    backlogRequestId: UUID,
    serviceName: String,
    status: BacklogRequestStatus,
  ): ServiceSummary?

  /**
   * Only used in tests.
   */
  @Query(
    nativeQuery = true,
    value = "SELECT * FROM service_summary s WHERE s.backlog_request_id = :backlogRequestId",
  )
  fun findByBacklogRequestId(@Param("backlogRequestId") backlogRequestId: UUID): List<ServiceSummary>

  @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = BACKLOG_REQUEST_LOCK_TIMEOUT)])
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    "DELETE FROM service_summary " +
      "WHERE id IN " +
      "(" +
      "SELECT s.id FROM backlog_request b " +
      "INNER JOIN service_summary s on s.backlog_request_id = b.id " +
      "WHERE b.version = :version" +
      ")",
    nativeQuery = true
  )
  fun deleteServiceSummaryByBacklogRequestVersion(@Param("version") version: String): Unit
}
