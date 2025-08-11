package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.backlog.utils

import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestOverview
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.io.BufferedWriter
import java.time.Duration

class BacklogApiClient(apiUrl: String) {

  private var httpClient: HttpClient = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(30))

  private var webclient: WebClient = WebClient.builder()
    .clientConnector(ReactorClientHttpConnector(httpClient))
    .baseUrl(apiUrl)
    .build()

  companion object {
    private val logger = LoggerFactory.getLogger(BacklogApiClient::class.java)
  }

  fun submitBacklogRequest(
    rowIndex: Int,
    request: CreateBacklogRequest,
    authToken: String,
    errorWriter: BufferedWriter,
  ) {
    val resp = webclient
      .post()
      .uri("/subject-access-request/backlog")
      .header("Authorization", "bearer $authToken")
      .bodyValue(request)
      .exchangeToMono(responseHandler(rowIndex, request, errorWriter))
      .block()

    resp?.let {
      logger.info(
        "Backlog request was created successfully rowIndex:[{}] {}\n",
        rowIndex,
        resp.body?.sarCaseReferenceNumber,
      )
    }
  }

  private fun responseHandler(
    rowIndex: Int,
    request: CreateBacklogRequest,
    errorWriter: BufferedWriter,
  ) = { response: ClientResponse ->
    when {
      response.statusCode().is2xxSuccessful -> {
        response.toEntity(BacklogRequestOverview::class.java)
      }

      response.statusCode().value() == 400 -> {
        response.bodyToMono(ErrorResponse::class.java)
          .doOnNext { error ->
            logger.error("create backlog request unsuccessful rowIndex:[{}] BAD REQUEST", rowIndex)
            logger.error("body: {}\n", error.summary())
            errorWriter.logFailedRequest(
              rowIndex,
              response.statusCode().value(),
              request.sarCaseReferenceNumber,
              error.summary(),
            )
            response.releaseBody()
          }
          .then(response.releaseBody().then(Mono.empty()))
      }

      response.statusCode().value() == 401 -> {
        logger.error("create backlog request unsuccessful rowIndex:[{}] UNAUTHORIZED", rowIndex)
        errorWriter.logFailedRequest(rowIndex, response.statusCode().value(), request.sarCaseReferenceNumber, null)
        response.releaseBody().then(Mono.error(RuntimeException("API request failed with unauthorized")))
      }

      else -> {
        response.bodyToMono(ErrorResponse::class.java)
          .flatMap { error ->
            errorWriter.logFailedRequest(
              rowIndex,
              response.statusCode().value(),
              request.sarCaseReferenceNumber,
              error.summary(),
            )

            Mono.error(
              RuntimeException(
                "create backlog request error: rowIndex:[$rowIndex], status: ${
                  response.statusCode().value()
                } body: ${error.summary()}",
              ),
            )
          }
      }
    }
  }

  private fun BufferedWriter.logFailedRequest(
    rowIndex: Int,
    status: Int,
    sarCaseRef: String,
    error: String? = null,
  ) {
    this.newLine()
    this.write("row: $rowIndex, sar_case_number: $sarCaseRef, status: $status, responseBody: $error")
    this.flush()
  }

  private fun ErrorResponse.summary(): String = "$status: $userMessage"
}
