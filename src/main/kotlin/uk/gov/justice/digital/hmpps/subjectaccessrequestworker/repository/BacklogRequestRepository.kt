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
      "SELECT summary.serviceName FROM BacklogRequest request " +
      "INNER JOIN ServiceSummary summary ON summary.backlogRequest.id = request.id " +
      "WHERE request.id = :backlogRequestId " +
      "AND summary.serviceName = cfg.serviceName " +
      "AND summary.status = 'COMPLETE'" +
      ") " +
      "ORDER BY cfg.order"
  )
  fun getOutstandingServiceSummaries(@Param("backlogRequestId") id: UUID): List<String>
}
