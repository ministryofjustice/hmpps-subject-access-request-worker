package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.requestLoader

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.PrematureCloseException
import reactor.util.retry.Retry
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.io.FileReader
import java.time.Duration
import java.time.LocalDate

const val NOMIS_ID_INDEX = 2
const val DATE_FROM_INDEX = 3
const val DATE_TO_INDEX = 4
const val DELIUS_CRN_INDEX = 5
const val INPUT_CSV = ""
const val SAR_ENDPOINT = ""

val allServices: List<String> = listOf(
  "hmpps-accredited-programmes-api",
  "hmpps-activities-management-api",
  "hmpps-uof-data-api",
  "hmpps-restricted-patients-api",
  "keyworker-api",
  "create-and-vary-a-licence-api",
  "hmpps-x-ray-body-scans-api",
  "G2",
  "G1",
  "hmpps-book-secure-move-api",
  "hmpps-complexity-of-need",
  "hmpps-resettlement-passport-api",
  "offender-management-allocation-manager",
  "hmpps-hdc-api",
  "hmpps-community-payback-api",
  "court-case-service",
  "hmpps-manage-adjudications-api",
  "hmpps-interventions-service",
  "hmpps-single-accommodation-service-api",
  "hmpps-education-and-work-plan-api",
  "offender-case-notes",
  "hmpps-accredited-programmes-manage-and-deliver-api",
  "hmpps-remand-and-sentencing-api",
  "hmpps-offender-categorisation-api",
  "hmpps-education-employment-api",
  "hmpps-jobs-board-api",
  "hmpps-book-a-video-link-api",
  "hmpps-managing-prisoner-apps-api",
  "hmpps-official-visits-api",
  "hmpps-support-additional-needs-api",
  "make-recall-decision-api",
  "hmpps-approved-premises-api",
  "hmpps-health-and-medication-api",
)

fun main(args: Array<String>) {
  val authToken = System.getenv("AUTH_TOKEN")
  SarRequestLoader(INPUT_CSV, authToken).createSubjectAccessRequests()
}

class SarRequestLoader(
  filepath: String,
  val authToken: String,
) {

  private val reader = FileReader(filepath)

  private val parser = CSVParser
    .builder()
    .setFormat(CSVFormat.Builder.create().setHeader().setSkipHeaderRecord(false).get())
    .setReader(reader)
    .get()

  private var httpClient: HttpClient = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(30))

  private var webclient: WebClient = WebClient.builder()
    .clientConnector(ReactorClientHttpConnector(httpClient))
    .baseUrl(SAR_ENDPOINT)
    .build()

  private companion object {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val mapper = ObjectMapper().registerModule(JavaTimeModule())
  }

  fun getCsvSequence(): Sequence<CreateSubjectAccessRequestEntity> = parser
    .stream()
    .limit(500)
    .iterator()
    .asSequence()
    .mapIndexed { rowIndex, line ->
      val nomisId = line[NOMIS_ID_INDEX].takeIf { it.isNotBlank() && it.length > 3 }
      val ndeliusId = line[DELIUS_CRN_INDEX].takeIf { it.isNotBlank() && nomisId.isNullOrEmpty() }

      CreateSubjectAccessRequestEntity(
        sarCaseReferenceNumber = "perfTes-$rowIndex",
        nomisId = nomisId,
        dateFrom = line[DATE_FROM_INDEX].takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) },
        dateTo = line[DATE_TO_INDEX].takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) },
        ndeliusId = ndeliusId,
        rowIndex = rowIndex,
        services = allServices
      )
    }

  fun createSubjectAccessRequests() {
    getCsvSequence().forEachIndexed { index, details ->
      postRequest(details)
    }
  }

  fun postRequest(request: CreateSubjectAccessRequestEntity) {
    webclient
      .post()
      .uri("/api/subjectAccessRequest")
      .header("Authorization", "bearer $authToken")
      .bodyValue(request)
      .retrieve()
      .onStatus({ it.value() == 401 }) { response ->
        Mono.error(RuntimeException("Token invalid ${response.statusCode()}"))
      }
      .onStatus({ it.isError }) { response ->
        response.bodyToMono(ErrorResponse::class.java)
          .doOnNext { body ->
            logger.error(
              "create subject access request unsuccessful row:[{}] status: {}",
              request.rowIndex,
              response.statusCode().value(),
            )
            logger.error("rowIndex: {}, status: {}, body: {}\n", request.rowIndex, response.statusCode(), body)
          }.then(Mono.empty())
      }.bodyToMono(String::class.java)
      .retryWhen(
        Retry.backoff(3, Duration.ofSeconds(3))
          .filter { throwable ->
            logger.error(throwable.message)
            when (throwable) {
              is WebClientRequestException -> throwable.cause is PrematureCloseException
              is PrematureCloseException -> true
              else -> false
            }
          }
          .doBeforeRetry { signal ->
            logger.warn("encountered PrematureCloseException backing off before attempting retry")
          }
          .onRetryExhaustedThrow { _, signal -> signal.failure() },
      ).doOnNext { _ ->
        logger.info("create subject access request success row:[{}]", request.rowIndex)
      }
      .doOnDiscard(String::class.java) { discarded -> println("Discarded: $discarded") }
      .block()
  }

  data class CreateSubjectAccessRequestEntity(
    val nomisId: String? = null,

    val ndeliusId: String? = null,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    val dateFrom: LocalDate? = null,

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    var dateTo: LocalDate? = null,

    val sarCaseReferenceNumber: String? = null,

    val services: List<String> = emptyList(),

    @JsonIgnore
    val rowIndex: Int,
  ) {
    override fun toString(): String {
      return mapper.writeValueAsString(this)
    }
  }
}