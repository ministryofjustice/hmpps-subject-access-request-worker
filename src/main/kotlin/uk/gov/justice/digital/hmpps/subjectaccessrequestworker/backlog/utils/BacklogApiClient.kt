package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.backlog.utils

import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestOverview
import java.io.BufferedWriter

internal class BacklogApiClient(apiUrl: String) {
  private val webclient: WebClient = WebClient.create(apiUrl)

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
            errorWriter.logFailedRequest(rowIndex, response.statusCode().value(), request.sarCaseReferenceNumber, body)
          }
          .then(Mono.empty())
      }

      response.statusCode().value() == 401 -> {
        errorWriter.logFailedRequest(rowIndex, response.statusCode().value(), request.sarCaseReferenceNumber, null)
        Mono.empty()
      }

      else -> {
        response.bodyToMono(String::class.java)
          .flatMap { body ->
            errorWriter.logFailedRequest(rowIndex, response.statusCode().value(), request.sarCaseReferenceNumber, body)

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
}
