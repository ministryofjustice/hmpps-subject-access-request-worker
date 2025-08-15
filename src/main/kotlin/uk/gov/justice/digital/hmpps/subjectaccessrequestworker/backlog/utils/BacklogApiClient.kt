package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.backlog.utils

import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.PrematureCloseException
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestOverview
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BacklogApiClient(apiUrl: String) {

  private var httpClient: HttpClient = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(30))

  private var webclient: WebClient = WebClient.builder()
    .clientConnector(ReactorClientHttpConnector(httpClient))
    .baseUrl(apiUrl)
    .build()

  private val errorLogFile: File = File(System.getenv("ERROR_LOG")).also { if (!it.exists()) it.createNewFile() }
  private val errorLogWriter: ErrorWriter = ErrorWriter(errorLogFile)

  companion object {
    private val logger = LoggerFactory.getLogger(BacklogApiClient::class.java)
  }

  fun submitBacklogRequest(
    rowIndex: Int,
    request: CreateBacklogRequest,
    authToken: String,
    resultsWriter: ResultsWriter,
  ) {
    logger.info(
      "create request: row[{}] {}, {}, {}, {}",
      rowIndex,
      request.sarCaseReferenceNumber,
      request.subjectName,
      request.nomisId,
      request.ndeliusCaseReferenceId,
    )

    webclient
      .post()
      .uri("/subject-access-request/backlog")
      .header("Authorization", "bearer $authToken")
      .bodyValue(request)
      .retrieve()
      .onStatus({ it.value() == 401 }) { response ->
        Mono.error(RuntimeException("Token invalid ${response.statusCode()}"))
      }
      .onStatus({ it.isError }) { response ->
        // Consume and release the body even on error
        response.bodyToMono(ErrorResponse::class.java)
          .doOnNext { body ->
            logger.error(
              "create backlog request unsuccessful row:[{}] status: {}",
              rowIndex,
              response.statusCode().value(),
            )
            logger.error("body: {}\n", body.summary())
            resultsWriter.writeError(request, response.statusCode().value(), body)
          }
          .then(Mono.empty())
      }
      .bodyToMono(BacklogRequestOverview::class.java)
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
            errorLogWriter.log(request, signal.failure())
          }
          .onRetryExhaustedThrow { _, signal -> signal.failure() },
      ).doOnNext { _ ->
        logger.info("create backlog request success row:[{}]", rowIndex)
        resultsWriter.writeSuccess(request)
      }
      .doOnDiscard(String::class.java) { discarded -> println("Discarded: $discarded") }
      .block()
  }

  private fun ErrorResponse.summary(): String = "$status: $userMessage"

  class ErrorWriter(file: File) : BufferedWriter(FileWriter(file)) {

    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun log(request: CreateBacklogRequest, throwable: Throwable) {
      this.write("${LocalDateTime.now().format(formatter)}: [${request.rowIndex}], ${request.sarCaseReferenceNumber} ${throwable::class.simpleName}: ${throwable.message}")
      this.newLine()
      this.flush()
    }
  }
}
