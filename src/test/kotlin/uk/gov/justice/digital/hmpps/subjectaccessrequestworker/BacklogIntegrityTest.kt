package uk.gov.justice.digital.hmpps.subjectaccessrequestworker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.io.File
import java.util.Optional

const val INPUT_CSV_PATH = "PATH_TO_INPUT_CSV_HERE"

class BacklogIntegrityTest {

  private val webClient = WebClient.create()

  private companion object {
    private val LOG = LoggerFactory.getLogger(BacklogIntegrityTest::class.java)
  }

  @Test
  fun `backlog csv report contains the correct values when checked against the individual hmpps SAR service endpoints`() {
    val services = getServicesConfig()

    File(INPUT_CSV_PATH).useLines { lines ->
      val header = mutableListOf<String>()

      lines.forEachIndexed { rowIndex, line ->
        header
          .takeIf { it.isEmpty() }
          ?.let {
            header.addAll(line.split(","))
            verifyHeader(header, services)
          }
          ?: run { verifyRowAgainstServiceApiData(rowIndex, line, services) }
      }
    }
  }

  private fun getServicesConfig(): List<Service> {
    val x = javaClass.getResource("/services.json").toURI()
    return ObjectMapper().readValue(x.toURL(), object : TypeReference<List<Service>>() {})
  }

  private fun verifyRowAgainstServiceApiData(
    rowIndex: Int,
    line: String,
    services: List<Service>,
  ) {
    LOG.info("verifying row[$rowIndex]")
    val csvRowData = mapToBacklogResult(line, 9 + services.size)

    val apiDataHeldResults = mutableListOf<Boolean>()
    services.forEachIndexed { serviceIndex, service ->
      assertThat(service.url).isNotNull()
      assertThat(service.name).isNotNull()

      val response = executeSarRequest(
        serviceUrl = service.url,
        nomisId = csvRowData.nomisId,
        ndeliusId = csvRowData.ndeliusId,
        dateFrom = csvRowData.dateFrom,
        dateTo = csvRowData.dateTo,
      )

      assertThat(response).isNotNull
      val serviceHoldsSubjectDataHeld = getServiceDataHeldResult(rowIndex, service.name!!, response!!)

      LOG.info(
        "row[{}] {} -> '{}' expect: {}, actual: {}",
        rowIndex,
        csvRowData.getSubjectId(),
        service.name,
        csvRowData.summaries[serviceIndex],
        serviceHoldsSubjectDataHeld,
      )
      apiDataHeldResults.add(serviceHoldsSubjectDataHeld)
    }

    csvRowData.summaries.forEachIndexed{ index, expected ->
      assertThat(expected)
        .withFailMessage("row[$rowIndex] ${csvRowData.getSubjectId()} -> column[${9 + index}] '${services[index].name}' expected $expected, actual ${apiDataHeldResults[index]}")
        .isEqualTo(apiDataHeldResults[index])
    }

    assertThat(csvRowData.summaries).containsExactlyElementsOf(apiDataHeldResults)

    val subjectDataHeldByAtLeastOneService = apiDataHeldResults.any { it }
    assertThat(csvRowData.dataHeld)
      .withFailMessage("row[$rowIndex] ${csvRowData.getSubjectId()} -> 'Data Held' expected ${csvRowData.dataHeld}, actual $subjectDataHeldByAtLeastOneService")
      .isEqualTo(subjectDataHeldByAtLeastOneService)
  }

  private fun verifyHeader(actual: List<String>, services: List<Service>) {
    LOG.info("verifying csv header")
    assertThat(actual.size).isEqualTo(services.size + 9)
    var index = 0
    assertThat(actual[index++]).isEqualTo("ID")
    assertThat(actual[index++]).isEqualTo("SAR Case Reference Number")
    assertThat(actual[index++]).isEqualTo("Subject Name")
    assertThat(actual[index++]).isEqualTo("Nomis Id")
    assertThat(actual[index++]).isEqualTo("Ndelius Case Reference Id")
    assertThat(actual[index++]).isEqualTo("Date From")
    assertThat(actual[index++]).isEqualTo("Date To")
    assertThat(actual[index++]).isEqualTo("Query Completed at")
    assertThat(actual[index++]).isEqualTo("Data Held")

    services.forEach { service -> assertThat(actual[index++]).isEqualTo(service.label) }
    LOG.info("verify csv header complete")
  }

  private fun executeSarRequest(
    serviceUrl: String?,
    nomisId: String? = null,
    ndeliusId: String? = null,
    dateFrom: String,
    dateTo: String,
  ) = webClient.mutate()
    .baseUrl(serviceUrl!!).build()
    .get()
    .uri {
      it.path("/subject-access-request")
        .queryParamIfPresent("prn", Optional.ofNullable(nomisId))
        .queryParamIfPresent("crn", Optional.ofNullable(ndeliusId))
        .queryParam("fromDate", dateFrom)
        .queryParam("toDate", dateTo)
        .build()
    }
    .header("Authorization", "Bearer ${System.getenv()["T_IS_FOR_TOKEN"]}") // Set auth token in env vars.
    .retrieve()
    .onStatus(HttpStatusCode::is5xxServerError) { clientResponse ->
      clientResponse.bodyToMono(String::class.java).flatMap { errorBody ->
        Mono.error(RuntimeException(errorBody))
      }
    }
    .toBodilessEntity()
    .block()

  private fun getServiceDataHeldResult(
    rowIndex: Int,
    serviceName: String,
    response: ResponseEntity<Void>,
  ) = when (response.statusCode.value()) {
    200 -> true
    204 -> false
    209 -> false
    else -> {
      fail("Unexpected status code ${response.statusCode.value()}, $serviceName, row[$rowIndex]")
    }
  }

  private fun mapToBacklogResult(line: String, expectedSize: Int) = line.split(",").let { columns ->
    assertThat(columns.size).isEqualTo(expectedSize)
    var index = 0
    BacklogResult(
      id = columns[index++],
      sarCaseReference = columns[index++],
      subjectName = columns[index++],
      nomisId = columns[index++].takeIf { it.isNotEmpty() },
      ndeliusId = columns[index++].takeIf { it.isNotEmpty() },
      dateFrom = columns[index++],
      dateTo = columns[index++],
      dateCompletedAt = columns[index++],
      dataHeld = columns[index++].toBoolean(),
      summaries = columns.subList(index, columns.size).map { it.toBoolean() },
    )
  }

  data class BacklogResult(
    val id: String,
    val sarCaseReference: String,
    val subjectName: String,
    val nomisId: String?,
    val ndeliusId: String?,
    val dateFrom: String,
    val dateTo: String,
    val dateCompletedAt: String,
    val dataHeld: Boolean,
    val summaries: List<Boolean>,
  ) {
    override fun toString(): String = ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this)
    fun getSubjectId() = nomisId ?: ndeliusId
  }

  data class Service(var name: String?, var label: String?, var url: String?) {
    constructor() : this(name = null, label = null, url = null)
  }
}