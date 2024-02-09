package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import java.time.Duration

@Service
class SubjectAccessRequestWorkerService(@Autowired val clientService: WebClientService) {

  fun startPolling() {
    val webClient = clientService.getClient("https://localhost:3000")
    val chosenSAR = this.pollForNewSubjectAccessRequests(webClient)
    val patchResponseCode = clientService.claim(webClient, chosenSAR)
    if (patchResponseCode == HttpStatusCode.valueOf(200)) {
      doReport(chosenSAR)
      clientService.complete(webClient, chosenSAR)
    }
    return
  }

  fun pollForNewSubjectAccessRequests(client: WebClient): SubjectAccessRequest {
    var response: Array<SubjectAccessRequest>?
    do {
      response = clientService.getUnclaimedSars("/api/subjectAccessRequests?unclaimed=true", client)
      Thread.sleep(Duration.ofSeconds(1))
    } while (response == null) // .isEmpty())
    // CHOOSE ONE FROM THE RESPONSE LIST
    return response.first()
  }

  fun doReport(sar: SubjectAccessRequest) {
    print("Would do report")
  }
}