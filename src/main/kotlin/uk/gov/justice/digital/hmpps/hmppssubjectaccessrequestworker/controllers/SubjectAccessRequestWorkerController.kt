package uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.controllers

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.hmppssubjectaccessrequestworker.models.SubjectAccessRequest
import java.time.Duration

@RestController
class SubjectAccessRequestWorkerController() {
  @EventListener(
    ApplicationReadyEvent::class,
  )
  fun startPolling() {
    print("STARTED POLLING")
//    val webClient: WebClient = WebClient.create("https://localhost:3000")
//    val chosenSAR: SubjectAccessRequest = this.pollForNewSubjectAccessRequests(webClient)
//    val patchResponse = webClient.patch().uri("/users/{id}" + chosenSAR.id.toString()).retrieve() // .bodyToMono(String::class.java)
//    val code = patchResponse.toBodilessEntity().block()?.statusCode
//    if (code == HttpStatusCode.valueOf(200)) {
//      doReport(chosenSAR)
//      webClient.patch().uri("/users/{id}/claim")
//    }
  }

  fun pollForNewSubjectAccessRequests(client: WebClient): SubjectAccessRequest {
    var response: Array<SubjectAccessRequest>?
    do {
      response = client.get().uri("/api/subjectAccessRequests?unclaimed=true").retrieve().bodyToMono(Array<SubjectAccessRequest>::class.java).block()
      Thread.sleep(Duration.ofSeconds(10))
    } while (response == null) // .isEmpty())
    // CHOOSE ONE FROM THE RESPONSE LIST
    return response.first()
  }

  fun doReport(sar: SubjectAccessRequest) {
    print("Would do report")
  }
}
