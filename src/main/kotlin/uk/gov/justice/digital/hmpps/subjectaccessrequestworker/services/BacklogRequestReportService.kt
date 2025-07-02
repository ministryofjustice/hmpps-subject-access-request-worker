package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.models.BacklogRequest
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.services.BacklogRequestService.BacklogVersionStatus
import java.io.BufferedWriter
import java.io.StringWriter
import java.time.format.DateTimeFormatter

@Service
class BacklogRequestReportService(
  val backlogRequestService: BacklogRequestService,
  val serviceConfigurationService: ServiceConfigurationService,
) {

  private val delimiter = ","
  private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
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

  @Transactional
  fun generateReport(version: String): String? {
    val versionStatus = backlogRequestService.getStatusByVersion(version)
      ?: throw BacklogVersionNotFoundException("backlog version $version not found")

    return versionStatus.takeIf { it.status == BacklogVersionStatus.COMPLETE }
      ?.let {
        val writer = StringWriter()
        val bufferedWriter = BufferedWriter(writer)

        bufferedWriter.use {
          writeHeaderRow(it)
          writeRows(it, version)
          it.flush()
        }
        writer.toString()
      } ?: throw BacklogVersionIncompleteException("backlog version: $version is not COMPLETE")
  }

  private fun writeHeaderRow(writer: BufferedWriter) {
    val requestFieldColumns = requestCsvHeadersColumns.joinToString(delimiter)
    val serviceColumns = serviceConfigurationService.getAllOrdered().joinToString(delimiter) { it.label }
    writer.write("$requestFieldColumns,$serviceColumns\n")
  }

  private fun writeRows(writer: BufferedWriter, version: String) {
    backlogRequestService.streamBacklogRequestForVersion(version)?.use { stream ->
      stream.forEach { backlogRequest -> writeRow(writer, backlogRequest) }
    }
  }

  private fun writeRow(writer: BufferedWriter, backlogRequest: BacklogRequest) {
    writer.write(backlogRequest.sarCaseReferenceNumber)
    writer.write(delimiter)
    writer.write(backlogRequest.subjectName.replace(",", ""))
    writer.write(delimiter)
    writer.write(backlogRequest.nomisId ?: "")
    writer.write(delimiter)
    writer.write(backlogRequest.ndeliusCaseReferenceId ?: "")
    writer.write(delimiter)
    writer.write(dateFormat.format(backlogRequest.dateFrom))
    writer.write(delimiter)
    writer.write(dateFormat.format(backlogRequest.dateTo))
    writer.write(delimiter)
    writer.write(backlogRequest.dataHeld.toString())
    writer.write(delimiter)
    writer.write(dateTimeFormat.format(backlogRequest.completedAt))
    writer.write(delimiter)
    writer.write(
      backlogRequest.serviceSummary.joinToString(delimiter) {
        it.dataHeld.toString()
      },
    )
    writer.write("\n")
    writer.flush()
  }

  open class BacklogReportException(message: String) : RuntimeException(message)

  class BacklogVersionIncompleteException(message: String) : BacklogReportException(message)

  class BacklogVersionNotFoundException(message: String) : BacklogReportException(message)
}
