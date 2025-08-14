package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.backlog.utils

import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import uk.gov.justice.digital.hmpps.subjectaccessrequestworker.controller.entity.BacklogRequestOverview
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.Duration

class BacklogApiClient(apiUrl: String) {

  private var provider: ConnectionProvider = ConnectionProvider.builder("customPool")
    .maxConnections(100) // Max total connections
    .pendingAcquireTimeout(Duration.ofSeconds(60)) // Wait time for acquiring a connection
    .maxIdleTime(Duration.ofMinutes(10)) // Max idle time before closing
    .maxLifeTime(Duration.ofMinutes(30)) // Max lifetime of a connection
    .build()

  private var httpClient: HttpClient = HttpClient.create(provider)
    .responseTimeout(Duration.ofSeconds(60))

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

    try {
      webclient
        .post()
        .uri("/subject-access-request/backlog")
        .header("Authorization", "bearer $authToken")
        .bodyValue(request)
        .retrieve()
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
        .doOnNext { _ ->
          logger.info("create backlog request success row:[{}]", rowIndex)
          resultsWriter.writeSuccess(request)
        }
        .doOnDiscard(String::class.java) { discarded -> println("Discarded: $discarded") }
        .block()
    } catch (e: Exception) {
      logger.error("row[$rowIndex]: {}", rowIndex, e)
    }
  }

  private fun ErrorResponse.summary(): String = "$status: $userMessage"
}
