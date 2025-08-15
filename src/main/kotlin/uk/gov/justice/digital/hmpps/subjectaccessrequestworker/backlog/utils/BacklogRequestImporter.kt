package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.backlog.utils

import com.fasterxml.jackson.databind.ObjectMapper
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Duration
import java.time.LocalDateTime

fun main(args: Array<String>) {
  if (args.size < 4) {
    throw RuntimeException("expected 4 input arguments.")
  }

  val apiUrl = getTargetApiUrl(args)
  val apiClient = BacklogApiClient(apiUrl)
  val requestSupplier = CsvBacklogRequestSupplier(
    version = args[0],
    file = args[1],
  )

  insertRequests(
    requestSupplier = requestSupplier,
    authToken = args[2],
    backlogApiClient = apiClient,
  )

  println("Failed requests are logged here: file:///${System.getenv("IMPORT_REPORT_CSV")}")
}

fun getTargetApiUrl(args: Array<String>): String = when {
  args[3] == "dev" -> "https://subject-access-request-worker-dev.hmpps.service.justice.gov.uk"
  args[3] == "preprod" -> "https://subject-access-request-worker-preprod.hmpps.service.justice.gov.uk"
  else -> throw RuntimeException("unknown env arg (accepted values: dev, preprod)")
}.also { println("targetApiUrl: $it") }

fun insertRequests(
  requestSupplier: BacklogRequestSupplier,
  authToken: String,
  backlogApiClient: BacklogApiClient,
) {
  val start = LocalDateTime.now()
  val resultsCsv = File(System.getenv("IMPORT_REPORT_CSV"))
    .also { if (!it.exists()) it.createNewFile() }

  ResultsWriter(resultsCsv).use { writer ->
    requestSupplier.get().forEachIndexed { i, request ->
      val rowIndex = i + 2

      backlogApiClient.submitBacklogRequest(
        rowIndex = rowIndex,
        request = request,
        authToken = authToken,
        resultsWriter = writer,
      )
    }
  }
  val end = LocalDateTime.now()
  println("Run duration: ${Duration.between(start, end)}")
}

interface BacklogRequestSupplier {

  fun get(): Sequence<CreateBacklogRequest>
}

data class CreateBacklogRequest(
  val sarCaseReferenceNumber: String,
  val subjectName: String,
  val version: String,
  val nomisId: String? = null,
  val ndeliusCaseReferenceId: String? = null,
  val dateFrom: String,
  val dateTo: String,
  val rowIndex: Int,
) {
  override fun toString(): String = ObjectMapper().writeValueAsString(this)
}

class ResultsWriter(file: File) : BufferedWriter(FileWriter(file)) {
  init {
    writeLine(
      arrayOf(
        "sar_case_number,version",
        "sar_full_name",
        "nomis_id",
        "delius_crn",
        "date_from",
        "date_to",
        "response_status",
        "error_details",
      ),
    )
  }

  fun writeSuccess(req: CreateBacklogRequest) = writeLine(
    arrayOf(
      req.sarCaseReferenceNumber,
      req.version,
      req.subjectName,
      req.nomisId,
      req.ndeliusCaseReferenceId,
      req.dateFrom,
      req.dateTo,
      "201",
      null,
    ),
  )

  fun writeError(req: CreateBacklogRequest, status: Int, response: ErrorResponse?) = writeLine(
    arrayOf(
      req.sarCaseReferenceNumber,
      req.version,
      req.subjectName,
      req.nomisId,
      req.ndeliusCaseReferenceId,
      req.dateFrom,
      req.dateTo,
      status.toString(),
      "${response?.userMessage}",
    ),
  )

  private fun writeLine(values: Array<String?>) {
    write(values.joinToString(","))
    newLine()
  }
}
