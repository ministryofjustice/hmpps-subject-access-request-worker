package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.backlog.utils

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.FileReader

const val EXPECTED_NUMBER_OF_COLUMNS = 15
const val SAR_CASE_REF_INDEX = 0
const val SUBJECT_NAME_INDEX = 1
const val NOMIS_ID_INDEX = 2
const val DATE_FROM_INDEX = 5
const val DATE_TO_INDEX = 6
const val DELIUS_CRN_INDEX = 14
const val LIMIT = 2000

class CsvBacklogRequestSupplier(val version: String, val file: String) : BacklogRequestSupplier {

  private val reader = FileReader(file)
  private val parser = CSVParser
    .builder()
    .setFormat(CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(false).get())
    .setReader(reader)
    .get()

  override fun get(): Sequence<CreateBacklogRequest> = parser
    .validateCsvHeader()
    .iterator()
    .asSequence()
    .map { line ->
      val nomisId = line[NOMIS_ID_INDEX].takeIf { it.isNotBlank() && it.length > 3 }
      val ndeliusId = line[DELIUS_CRN_INDEX].takeIf { it.isNotBlank() && nomisId.isNullOrEmpty() }

      CreateBacklogRequest(
        version = "1",
        sarCaseReferenceNumber = line[SAR_CASE_REF_INDEX],
        subjectName = line[SUBJECT_NAME_INDEX],
        nomisId = nomisId,
        dateFrom = line[DATE_FROM_INDEX],
        dateTo = line[DATE_TO_INDEX],
        ndeliusCaseReferenceId = ndeliusId,
      )
    }.drop(1)
    .take(LIMIT)

  private fun CSVParser.validateCsvHeader(): CSVParser {
    with(this.headerNames) {
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
    return this
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
}
