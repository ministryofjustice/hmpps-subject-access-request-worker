package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.gateways.SubjectAccessRequestGateway
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import java.time.Duration

@Service
class SubjectAccessRequestWorkerService(
  @Autowired val sarGateway: SubjectAccessRequestGateway,
  @Value("\${services.poller.run-once}")
  private val runOnce: String? = "false",
  @Value("\${services.sar-api.base-url}")
  private val sarUrl: String,
) {
  fun startPolling() {
    val webClient = sarGateway.getClient(sarUrl)
    val token = sarGateway.getClientTokenFromHmppsAuth()
    val chosenSAR = this.pollForNewSubjectAccessRequests(webClient, token)
    val patchResponseCode = sarGateway.claim(webClient, chosenSAR, token)
    if (patchResponseCode == HttpStatusCode.valueOf(200)) {
      doReport(chosenSAR)
      sarGateway.complete(webClient, chosenSAR, token)
    }

    if (runOnce == "true") {
      return
    } else {
      startPolling()
    }
  }

  fun pollForNewSubjectAccessRequests(client: WebClient, token: String): SubjectAccessRequest {
    var response: Array<SubjectAccessRequest>? = emptyArray()

    while (response.isNullOrEmpty()) {
      Thread.sleep(Duration.ofSeconds(10))
      response = sarGateway.getUnclaimed(client, token)
    }
    return response.first()
  }

  fun doReport(sar: SubjectAccessRequest) {
    println("Would do report")
  }
}
