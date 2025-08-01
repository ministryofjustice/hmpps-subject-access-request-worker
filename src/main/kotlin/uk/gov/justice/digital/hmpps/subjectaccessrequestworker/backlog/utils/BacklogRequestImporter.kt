package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.backlog.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.doyaaaaaken.kotlincsv.client.CsvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestOverview
import java.io.File

const val INPUT_CSV_PATH = ""
const val EXPECTED_NUMBER_OF_COLUMNS = 15
const val SAR_CASE_REF_INDEX = 0
const val SUBJECT_NAME_INDEX = 1
const val NOMIS_ID_INDEX = 2
const val DATE_FROM_INDEX = 5
const val DATE_TO_INDEX = 6
const val DELIUS_CRN_INDEX = 14
const val CREATE_BACKLOG_API = "https://subject-access-request-worker-preprod.hmpps.service.justice.gov.uk"

fun main() {
  val backlogApiClient = BacklogApiClient(CREATE_BACKLOG_API)
  processBacklogCsv(backlogApiClient)
}

internal fun processBacklogCsv(backlogApiClient: BacklogApiClient) {
  val csv = readCsvWithQuotes(INPUT_CSV_PATH)
  validateCsvHeader(csv[0])

  run processLoop@{
    csv.subList(1, csv.size).forEachIndexed { rowIndex, row ->
      if (rowIndex >= 11) {
        println("Exit at row $rowIndex")
        return@processLoop
      }

      val request = mapRowToCreateBacklogRequest(row, "1")
      backlogApiClient.submitBacklogRequest(rowIndex, request, System.getenv("AUTH_TOKEN"))
    }
  }
}


private fun readCsvWithQuotes(filePath: String): List<List<String>> {
  val reader: CsvReader = csvReader()
  return reader.readAll(File(filePath))
}

private fun validateCsvHeader(header: List<String>) {
  with(header) {
    assertEquals(
      size,
      EXPECTED_NUMBER_OF_COLUMNS,
    ) { "expected csv to have $EXPECTED_NUMBER_OF_COLUMNS, but was $size" }
    assertEquals(this, SAR_CASE_REF_INDEX, "sar_case_number")
    assertEquals(this, SUBJECT_NAME_INDEX, "sar_full_name")
    assertEquals(this, NOMIS_ID_INDEX, "nomis_id")
    assertEquals(this, DATE_FROM_INDEX, "date_from")
    assertEquals(this, DATE_TO_INDEX, "date_to")
    assertEquals(this, DELIUS_CRN_INDEX, "delius_crn")
  }
}

private fun mapRowToCreateBacklogRequest(line: List<String>, version: String): CreateBacklogRequest {
  assertEquals(line.size, EXPECTED_NUMBER_OF_COLUMNS) { "expected $EXPECTED_NUMBER_OF_COLUMNS, but was ${line.size}" }

  val nomisId = line[NOMIS_ID_INDEX].takeIf { it.isNotBlank() && it.length > 3 }
  val ndeliusId = line[DELIUS_CRN_INDEX].takeIf { it.isNotBlank() && nomisId.isNullOrEmpty() }

  return CreateBacklogRequest(
    version = version,
    sarCaseReferenceNumber = line[SAR_CASE_REF_INDEX],
    subjectName = line[SUBJECT_NAME_INDEX],
    nomisId = nomisId,
    dateFrom = line[DATE_FROM_INDEX],
    dateTo = line[DATE_TO_INDEX],
    ndeliusCaseReferenceId = ndeliusId,
  )
}

private fun assertEquals(actual: Any?, expected: Any?, message: () -> String) {
  if (actual != expected) {
    throw AssertionError("validation exception: ${message()}")
  }
}

private fun assertEquals(header: List<String>, index: Int, expected: Any?) {
  if (header[index] != expected) {
    throw AssertionError("validation exception: header[$index] expected '$expected', actual: '${header[index]}'")
  }
}


internal class BacklogApiClient(apiUrl: String) {
  private val webclient: WebClient = WebClient.create(apiUrl)

  companion object {
    private val logger = LoggerFactory.getLogger(BacklogApiClient::class.java)
  }

  fun submitBacklogRequest(rowIndex: Int, request: CreateBacklogRequest, authToken: String) {
    val resp = webclient
      .post()
      .uri("/subject-access-request/backlog")
      .header("Authorization", "bearer $authToken")
      .bodyValue(request)
      .exchangeToMono(responseHandler(rowIndex))
      .block()

    resp?.let {
      logger.info(
        "Backlog request was created successfully rowIndex:[{}] {}\n",
        rowIndex,
        resp.body?.sarCaseReferenceNumber,
      )
    }
  }

  private fun responseHandler(rowIndex: Int) = { response: ClientResponse ->
    logger.info("response from API: ${response.statusCode().value()}")
    when {
      response.statusCode().is2xxSuccessful -> {
        response.toEntity(BacklogRequestOverview::class.java)
      }

      response.statusCode().value() == 400 -> {
        response.bodyToMono(String::class.java)
          .doOnNext { body ->
            logger.error("create backlog request unsuccessful rowIndex:[{}] BAD REQUEST", rowIndex)
            logger.error("body: {}\n", body)
          }
          .then(Mono.empty())
      }

      else -> {
        response.bodyToMono(String::class.java)
          .flatMap { body ->
            Mono.error(
              RuntimeException(
                "create backlog request error: rowIndex:[$rowIndex], status: ${
                  response.statusCode().value()
                } body: $body",
              ),
            )
          }
      }
    }
  }
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
  override fun toString(): String {
    return ObjectMapper().writeValueAsString(this)
  }
}
