package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import java.time.Duration

@Service
class SubjectAccessRequestWorkerService(
  @Autowired val clientService: WebClientService, @Value("\${services.poller.run-once}")
  private val runOnce: String? = "false"
) {
  fun startPolling() {
    val webClient = clientService.getClient("http://localhost:8080")
    val token = clientService.getToken()
    val chosenSAR = this.pollForNewSubjectAccessRequests(webClient, token)
    print("CHOSEN: ")
    print(chosenSAR)
    val patchResponseCode = clientService.claim(webClient, chosenSAR, token)
    print("PATCH RESPONSE: ")
    print(patchResponseCode)
    if (patchResponseCode == HttpStatusCode.valueOf(200)) {
      doReport(chosenSAR)
      clientService.complete(webClient, chosenSAR, token)
    }

    if(runOnce == "true") {
      return
    } else {
      startPolling()
    }
  }

  fun pollForNewSubjectAccessRequests(client: WebClient, token: String): SubjectAccessRequest {
    var response: Array<SubjectAccessRequest>? = emptyArray()

    while (response.isNullOrEmpty()) {
      Thread.sleep(Duration.ofSeconds(1))
      response = clientService.getUnclaimedSars(client, token)
      print("RESPONSE")
      print(response)
    }
    return response.first()
  }

  fun doReport(sar: SubjectAccessRequest) {
    print("Would do report ")
  }
}
