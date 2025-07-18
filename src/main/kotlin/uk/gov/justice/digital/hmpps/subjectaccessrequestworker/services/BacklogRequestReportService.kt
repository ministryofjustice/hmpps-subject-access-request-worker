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
  private val metadataColumns = listOf(
    "ID",
    "SAR Case Reference Number",
    "Subject Name",
    "Nomis Id",
    "Ndelius Case Reference Id",
    "Date From",
    "Date To",
    "Query Completed at",
    "Data Held",
  )

  private val getServiceSummariesQuery =
    "SELECT " +
      "s.data_held, s.backlog_request_id, cfg.list_order, cfg.label " +
      "FROM service_summary s " +
      "INNER JOIN service_configuration cfg ON cfg.id = s.service_configuration_id " +
      "WHERE backlog_request_id = ? " +
      "ORDER BY cfg.list_order ASC;"

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

      val headerRow = generateHeader()
      writeHeaderRow(writer, headerRow)
      writeBacklogReportForVersion(version, writer, headerRow)
      response.status = HttpStatus.OK.value()
    } ?: throw BacklogVersionIncompleteException("backlog version: $version is not COMPLETE")
  }

  private fun writeBacklogReportForVersion(
    version: String,
    writer: BufferedWriter,
    headerRow: List<String>,
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
        writeRowDetails(writer, rs, headerRow)
      }
    }
  }

  private fun writeRowDetails(writer: BufferedWriter, rs: ResultSet, headerRow: List<String>) {
    while (rs.next()) {
      val backlogRequestId = rs.getObject("id") as UUID

      writer.write(backlogRequestId.toString())
      writer.write(delimiter)
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

      getServiceSummariesForBacklogRequestId(backlogRequestId, writer, headerRow)
    }
  }

  private fun writeHeaderRow(writer: BufferedWriter, headerRow: List<String>) {
    writer.write("${headerRow.joinToString(delimiter)}\n")
    writer.flush()
  }

  private fun generateHeader(): List<String> {
    val headerRow = mutableListOf<String>()
    headerRow.addAll(metadataColumns)
    headerRow.addAll(serviceConfigurationService.getAllOrdered().map { it.label })
    return headerRow
  }

  private fun getServiceSummariesForBacklogRequestId(
    backlogRequestId: UUID,
    writer: BufferedWriter,
    headerRow: List<String>,
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
        writeServiceSummaryRowData(rs, writer, headerRow)
      }
    }
  }

  fun writeServiceSummaryRowData(rs: ResultSet, writer: BufferedWriter, headerRow: List<String>) {
    val serviceSummaryValues = mutableListOf<Boolean>()

    while (rs.next()) {
      val serviceDataHeld = rs.getBoolean("data_held")
      val serviceName = rs.getString("label")
      val listOrder = rs.getInt("list_order")
      val backlogRequestId = rs.getString("backlog_request_id")

      /**
       * The backlog report csv consists of 8 metadata columns followed by n 'data held' columns one for each SAR
       * service. The order of the services in the csv is determined by the 'list_order' value of each service in the
       * 'service_configuration' table.
       *
       * If service X has list_order 1 then the 'X Data Held' column is at index 9 (metadata column count + service list order value).
       **/
      val serviceColumnIndex = (metadataColumns.size - 1) + listOrder
      if (headerRow[serviceColumnIndex] != serviceName) {
        throw RuntimeException(
          "error writing row to Backlog report csv: backlogRequestId: $backlogRequestId, expected " +
            "service=${headerRow[serviceColumnIndex]} at column index[$serviceColumnIndex] but was $serviceName",
        )
      }

      serviceSummaryValues.add(serviceDataHeld)
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
