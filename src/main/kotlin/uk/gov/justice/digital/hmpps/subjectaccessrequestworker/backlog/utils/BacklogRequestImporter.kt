package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.backlog.utils

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
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

  println("Failed requests are logged here: file:///${System.getenv("IMPORT_ERRORS_CSV")}")
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
  getErrorWriter().use { errorWriter ->
    errorWriter.start()

    requestSupplier.get().forEachIndexed { rowIndex, request ->
      backlogApiClient.submitBacklogRequest(
        rowIndex = rowIndex,
        request = request,
        authToken = authToken,
        errorWriter = errorWriter,
      )
    }
    errorWriter.end()
  }
}

private fun getErrorWriter() = BufferedWriter(FileWriter(getErrorLogFile()))

private fun getErrorLogFile() = File(System.getenv("IMPORT_ERRORS_CSV")).also { if (!it.exists()) it.createNewFile() }

private fun BufferedWriter.start() {
  this.write("### Start: ${LocalDateTime.now()} ###")
  this.flush()
}

private fun BufferedWriter.end() {
  this.newLine()
  this.write("### End: ${LocalDateTime.now()} ###")
  this.flush()
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
) {
  override fun toString(): String = ObjectMapper().writeValueAsString(this)
}
