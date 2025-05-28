package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import java.util.UUID

@Repository
interface BacklogRequestRepository: JpaRepository<BacklogRequest, UUID> {

  @Query(
    "SELECT cfg.serviceName FROM ServiceConfiguration cfg " +
      "WHERE NOT EXISTS (" +
      "SELECT cfg.serviceName FROM BacklogRequest b " +
      "INNER JOIN BacklogRequestServiceSummary s ON s.backlog_request_id = b.id " +
      "WHERE b.id = :backlogRequestId " +
      "AND s.serviceName = cfg.serviceName " +
      ") " +
      "ORDER BY cfg.order"
  )
  fun getPendingServiceSummaries(@Param("backlogRequestId") id: UUID): List<String>
}