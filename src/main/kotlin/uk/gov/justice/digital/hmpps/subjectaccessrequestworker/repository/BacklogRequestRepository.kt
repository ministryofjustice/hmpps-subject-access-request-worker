package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequestStatus
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.ServiceConfiguration
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

@Repository
interface BacklogRequestRepository : JpaRepository<BacklogRequest, UUID> {

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

  fun findByIdAndStatus(@Param("id") id: UUID, status: BacklogRequestStatus): BacklogRequest?

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    "UPDATE BacklogRequest br SET br.claimDateTime = :currentDateTime " +
      "WHERE br.id = :id " +
      "AND br.status = 'PENDING' " +
      "AND (br.claimDateTime IS NULL OR br.claimDateTime < :backOffThreshold)",
  )
  fun claimRequest(
    @Param("id") id: UUID,
    @Param("backOffThreshold") backOffThreshold: LocalDateTime = now().minusMinutes(30),
    @Param("currentDateTime") currentDateTime: LocalDateTime = now(),
  ): Int

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    "UPDATE BacklogRequest SET status = 'COMPLETE' " +
      "WHERE id = :id " +
      "AND " +
      "(SELECT COUNT(DISTINCT cfg.serviceName) FROM ServiceConfiguration cfg) = " +
      "(SELECT COUNT(DISTINCT summary.serviceName) FROM ServiceSummary summary " +
      "WHERE summary.backlogRequest.id = :id AND summary.status = 'COMPLETE')",
  )
  fun completeRequest(@Param("id") id: UUID): Int
}
