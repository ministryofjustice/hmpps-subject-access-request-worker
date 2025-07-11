package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService.BacklogVersionStatus
import java.io.BufferedWriter
import java.sql.ResultSet
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.sql.DataSource

@Service
class BacklogRequestReportService(
  val backlogRequestService: BacklogRequestService,
  val serviceConfigurationService: ServiceConfigurationService,
  val dataSource: DataSource,
) {

  private val delimiter = ","
  private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss")
  private val requestCsvHeadersColumns = listOf(
    "SAR Case Reference Number",
    "Subject Name",
    "Nomis Id",
    "Ndelius Case Reference Id",
    "Date From",
    "Date To",
    "Data Held",
    "Query Completed at",
  )

  private val getServiceSummariesQuery =
    "SELECT data_held " +
      "FROM service_summary " +
      "WHERE backlog_request_id = ? " +
      "ORDER BY service_order ASC"

  private val getBacklogRequestsByVersionQuery =
    "SELECT " +
      "id, " +
      "sar_case_reference_number, " +
      "subject_name, nomis_id, " +
      "ndelius_case_reference_id, " +
      "date_from, " +
      "date_to, " +
      "data_held, " +
      "completed_at " +
      "FROM backlog_request " +
      "WHERE version = ? " +
      "AND status = 'COMPLETE' " +
      "ORDER BY sar_case_reference_number ASC, subject_name DESC;"

  @Transactional
  fun generateReportJdbc(version: String, response: HttpServletResponse) {
    val versionStatus = backlogRequestService.getStatusByVersion(version)
      ?: throw BacklogVersionNotFoundException("backlog version $version not found")

    versionStatus.takeIf { it.status == BacklogVersionStatus.COMPLETE }?.let {
      val writer = BufferedWriter(response.writer)
      response.contentType = "text/csv"
      response.setHeader("Content-Disposition", "attachment; filename=\"SAR-v$version.csv\"")

      writeHeaderRow(writer)
      writeBacklogReportForVersion(version, writer)
      response.status = HttpStatus.OK.value()
    } ?: throw BacklogVersionIncompleteException("backlog version: $version is not COMPLETE")
  }

  private fun writeBacklogReportForVersion(
    version: String,
    writer: BufferedWriter,
  ) = dataSource.connection.use { conn ->
    conn.autoCommit = false

    conn.prepareStatement(
      getBacklogRequestsByVersionQuery,
      ResultSet.TYPE_FORWARD_ONLY,
      ResultSet.CONCUR_READ_ONLY,
    ).use { stmt ->
      stmt.setString(1, version)
      stmt.fetchSize = 1000

      stmt.executeQuery().use { rs ->
        writeRowDetails(writer, rs)
      }
    }
  }

  private fun writeRowDetails(writer: BufferedWriter, rs: ResultSet) {
    while (rs.next()) {
      writer.write(rs.getString("sar_case_reference_number"))
      writer.write(delimiter)
      writer.write(rs.getString("subject_name").replace(",", ""))
      writer.write(delimiter)
      writer.write(rs.getStringOrEmpty("nomis_id"))
      writer.write(delimiter)
      writer.write(rs.getStringOrEmpty("ndelius_case_reference_id"))
      writer.write(delimiter)
      writer.write(rs.getStringOrEmpty("date_from"))
      writer.write(delimiter)
      writer.write(rs.getStringOrEmpty("date_to"))
      writer.write(delimiter)
      writer.write(dateTimeFormat.format(rs.getTimestamp("completed_at").toLocalDateTime()))

      val backlogRequestId = rs.getObject("id") as UUID
      getServiceSummariesForBacklogRequestId(backlogRequestId, writer)
    }
  }

  private fun writeHeaderRow(writer: BufferedWriter) {
    val requestFieldColumns = requestCsvHeadersColumns.joinToString(delimiter)
    val serviceColumns = serviceConfigurationService.getAllOrdered().joinToString(delimiter) { it.label }
    writer.write("$requestFieldColumns,$serviceColumns\n")
    writer.flush()
  }

  private fun getServiceSummariesForBacklogRequestId(
    backlogRequestId: UUID,
    writer: BufferedWriter,
  ) = dataSource.connection.use { conn ->
    conn.autoCommit = false

    conn.prepareStatement(
      getServiceSummariesQuery,
      ResultSet.TYPE_FORWARD_ONLY,
      ResultSet.CONCUR_READ_ONLY,
    ).use { stmt ->
      stmt.setObject(1, backlogRequestId)
      stmt.fetchSize = 1000

      stmt.executeQuery().use { rs ->
        writeServiceSummaryRowData(rs, writer)
      }
    }
  }

  fun writeServiceSummaryRowData(rs: ResultSet, writer: BufferedWriter) {
    val serviceSummaryValues = mutableListOf<Boolean>()
    while (rs.next()) {
      serviceSummaryValues.add(rs.getBoolean("data_held"))
    }

    // Set global data held value true if at least 1 service data held value is true
    writer.write(delimiter)
    writer.write((serviceSummaryValues.firstOrNull { it } ?: false).toString())
    writer.write(delimiter)

    writer.write(serviceSummaryValues.joinToString(","))
    writer.write("\n")
    writer.flush()
  }

  private fun ResultSet.getStringOrEmpty(name: String): String = this.getString(name) ?: ""

  open class BacklogReportException(message: String) : RuntimeException(message)

  class BacklogVersionIncompleteException(message: String) : BacklogReportException(message)

  class BacklogVersionNotFoundException(message: String) : BacklogReportException(message)
}
